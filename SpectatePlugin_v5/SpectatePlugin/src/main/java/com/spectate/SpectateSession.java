package com.spectate;

import org.bukkit.Location;

/**
 * Immutable saved state + mutable per-session toggles for one spectate session.
 * Fly has been removed — spectators use god-mode only; they cannot fly.
 */
public class SpectateSession {

    // ---------------------------------------------------------------
    //  Saved state (restored on /sp stop)
    // ---------------------------------------------------------------
    private final String   spectatorName;
    private final String   targetName;
    private final Location savedLocation;   // cloned copy
    private final int      savedHealth;
    private final boolean  savedIsInvulnerable;

    // ---------------------------------------------------------------
    //  Per-session toggles (all start at their default when /sp begins)
    // ---------------------------------------------------------------
    private boolean followEnabled = true;   // scheduler-based position tracking
    private boolean godEnabled    = true;   // NMS isInvulnerable
    private boolean freezeEnabled = false;  // cancel target move events

    public SpectateSession(String spectatorName,
                           String targetName,
                           Location savedLocation,
                           int savedHealth,
                           boolean savedIsInvulnerable) {
        this.spectatorName       = spectatorName;
        this.targetName          = targetName;
        this.savedLocation       = savedLocation;
        this.savedHealth         = savedHealth;
        this.savedIsInvulnerable = savedIsInvulnerable;
    }

    // --- Immutable getters ---
    public String   getSpectatorName()      { return spectatorName; }
    public String   getTargetName()         { return targetName; }
    public Location getSavedLocation()      { return savedLocation; }
    public int      getSavedHealth()        { return savedHealth; }
    public boolean  getSavedIsInvulnerable(){ return savedIsInvulnerable; }

    // --- Toggle methods — return the new state ---
    public boolean toggleFollow() { followEnabled = !followEnabled; return followEnabled; }
    public boolean toggleGod()    { godEnabled    = !godEnabled;    return godEnabled;    }
    public boolean toggleFreeze() { freezeEnabled = !freezeEnabled; return freezeEnabled; }

    // --- State getters ---
    public boolean isFollowEnabled() { return followEnabled; }
    public boolean isGodEnabled()    { return godEnabled;    }
    public boolean isFreezeEnabled() { return freezeEnabled; }
}
