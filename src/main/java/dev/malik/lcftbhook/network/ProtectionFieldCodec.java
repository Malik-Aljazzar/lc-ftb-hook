package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Kind;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Wire format of the active backend's protection fields with their current
 * prices - the client has no way to know which claim mod the server uses.
 */
final class ProtectionFieldCodec {
    private ProtectionFieldCodec() {
    }

    static void write(FriendlyByteBuf buffer, List<ProtectionProperty> fields) {
        buffer.writeVarInt(fields.size());
        for (ProtectionProperty field : fields) {
            ProtectionFieldSpec spec = field.spec();
            buffer.writeUtf(spec.id());
            buffer.writeEnum(spec.kind());
            buffer.writeBoolean(spec.accessListRelevant());
            buffer.writeBoolean(spec.primary());
            buffer.writeVarInt(spec.roles().size());
            spec.roles().forEach(buffer::writeEnum);
            buffer.writeLong(field.configPrice());
        }
    }

    static List<ProtectionProperty> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ProtectionProperty> fields = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf();
            Kind kind = buffer.readEnum(Kind.class);
            boolean accessListRelevant = buffer.readBoolean();
            boolean primary = buffer.readBoolean();
            Set<Role> roles = EnumSet.noneOf(Role.class);
            int roleCount = buffer.readVarInt();
            for (int r = 0; r < roleCount; r++) {
                roles.add(buffer.readEnum(Role.class));
            }
            long price = buffer.readLong();
            ProtectionFieldSpec spec = new ProtectionFieldSpec(id, kind, accessListRelevant, roles, price, "", primary);
            fields.add(new ProtectionProperty(spec, () -> price));
        }
        return fields;
    }
}
