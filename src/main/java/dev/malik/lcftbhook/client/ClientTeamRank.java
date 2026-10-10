package dev.malik.lcftbhook.client;

/** Local player's rank on their own team, from the server-synced {@link ClientClaims}. */
public final class ClientTeamRank {
    private ClientTeamRank() {
    }

    public static boolean isOfficerOrBetter() {
        return ClientClaims.isOwnOfficer();
    }
}
