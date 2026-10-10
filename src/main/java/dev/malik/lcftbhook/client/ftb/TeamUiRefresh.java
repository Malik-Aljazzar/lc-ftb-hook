package dev.malik.lcftbhook.client.ftb;

import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;

public final class TeamUiRefresh {
    private TeamUiRefresh() {
    }

    public static void refreshMyTeamScreenIfOpen() {
        MyTeamScreen screen = ClientUtils.getCurrentGuiAs(MyTeamScreen.class);
        if (screen != null) {
            screen.refreshWidgets();
        }
    }
}
