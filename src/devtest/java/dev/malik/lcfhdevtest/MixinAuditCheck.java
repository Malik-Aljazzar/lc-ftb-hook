package dev.malik.lcfhdevtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * {@code /lcfhtest mixinaudit}: loads every target class of this mod's own
 * mixin configs right now and checks that each of our mixins was actually
 * applied to it, instead of waiting until the game first happens to touch
 * each class - a rarely used FTB/OPAC class can otherwise stay unloaded for a
 * whole test session, and a broken target in it would go unnoticed. Run it
 * after every dependency bump. "Applied" = the target has a method carrying
 * {@link MixinMerged} for that mixin.
 * <p>
 * Only our own targets: Mixin's global {@code MixinEnvironment.audit()}
 * force-loads every mod's targets and dies on other mods' common mixins into
 * client classes (Sable, 2026-11-15). A target whose class doesn't exist means
 * its mod isn't installed (SKIP). A target that loads but lacks one of our
 * mixins is a FAIL - for a soft-fail config (OPAC, Numismatics, Xaero,
 * ScorchedGuns, Sable) that's the only place it shows up. What this can't see
 * is an injector inside an applied mixin that silently matched nothing
 * ({@code require = 0}); compare the target's bytecode for that (AGENTS.md).
 * On a dedicated server the {@code client} mixins are skipped - they need a
 * dev client.
 */
final class MixinAuditCheck {
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";

    private MixinAuditCheck() {
    }

    static int run(CommandSourceStack source) {
        ClassLoader loader = MixinAuditCheck.class.getClassLoader();
        List<String> failures = new ArrayList<>();
        Set<String> skippedTargets = new LinkedHashSet<>();
        int checked = 0;

        Map<String, Set<String>> mixinsByTarget = new TreeMap<>();
        for (String configName : ownConfigs()) {
            try {
                collectMixins(loader, configName, mixinsByTarget);
            } catch (Exception e) {
                failures.add("config " + configName + ": " + e);
            }
        }

        for (Map.Entry<String, Set<String>> entry : mixinsByTarget.entrySet()) {
            String target = entry.getKey();
            Set<String> applied;
            try {
                applied = mergedMixins(Class.forName(target, false, loader));
            } catch (ClassNotFoundException e) {
                skippedTargets.add(target);
                continue;
            } catch (Throwable t) {
                failures.add("loading " + target + " threw " + t);
                continue;
            }
            for (String mixin : entry.getValue()) {
                checked++;
                if (!applied.contains(mixin)) {
                    failures.add(simpleName(mixin) + " not applied to " + target);
                }
            }
        }

        for (String failure : failures) {
            source.sendFailure(Component.literal("FAIL " + failure));
        }
        if (!skippedTargets.isEmpty()) {
            String skipped = skippedTargets.stream().map(MixinAuditCheck::simpleName).collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("SKIP (mod not installed): " + skipped), false);
        }
        int finalChecked = checked;
        source.sendSuccess(() -> Component.literal((failures.isEmpty() ? "PASS" : "FAIL") + " mixin audit: "
                + finalChecked + " mixins checked on " + (mixinsByTarget.size() - skippedTargets.size())
                + " loaded targets, " + failures.size() + " failures"), false);
        return failures.isEmpty() ? 1 : 0;
    }

    /**
     * Mixin marks every method it merges into a target with a runtime-visible
     * {@link MixinMerged} naming the mixin - handlers, accessors and unique
     * helpers alike, so every one of our mixins leaves at least one.
     */
    private static Set<String> mergedMixins(Class<?> targetClass) {
        Set<String> mixins = new HashSet<>();
        for (Method method : targetClass.getDeclaredMethods()) {
            MixinMerged merged = method.getAnnotation(MixinMerged.class);
            if (merged != null) {
                mixins.add(merged.mixin());
            }
        }
        return mixins;
    }

    private static List<String> ownConfigs() {
        return MixinEnvironment.getCurrentEnvironment().getMixinConfigs().stream()
                .filter(name -> name.startsWith("lc_ftb_hook"))
                .toList();
    }

    private static void collectMixins(ClassLoader loader, String configName, Map<String, Set<String>> mixinsByTarget) throws Exception {
        JsonObject config;
        try (InputStream in = loader.getResourceAsStream(configName)) {
            if (in == null) {
                throw new IllegalStateException("not on the classpath");
            }
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String pkg = config.get("package").getAsString();
        List<String> sections = new ArrayList<>(List.of("mixins", "server"));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            sections.add("client");
        }
        for (String section : sections) {
            JsonArray names = config.getAsJsonArray(section);
            if (names == null) {
                continue;
            }
            for (JsonElement name : names) {
                String mixin = pkg + "." + name.getAsString();
                for (String target : targetsOf(loader, mixin)) {
                    mixinsByTarget.computeIfAbsent(target, t -> new LinkedHashSet<>()).add(mixin);
                }
            }
        }
    }

    /** Reads the {@code @Mixin} targets from the mixin's bytecode - Mixin forbids loading a mixin class itself. */
    private static List<String> targetsOf(ClassLoader loader, String mixin) throws Exception {
        byte[] bytes;
        try (InputStream in = loader.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
            if (in == null) {
                throw new IllegalStateException("mixin class " + mixin + " not found");
            }
            bytes = in.readAllBytes();
        }
        List<String> targets = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN_ANNOTATION.equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String ignored, Object value) {
                                if (value instanceof Type type) {
                                    targets.add(type.getClassName());
                                } else if (value instanceof String className) {
                                    targets.add(className.replace('/', '.'));
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (targets.isEmpty()) {
            throw new IllegalStateException("no @Mixin targets in " + mixin);
        }
        return targets;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
