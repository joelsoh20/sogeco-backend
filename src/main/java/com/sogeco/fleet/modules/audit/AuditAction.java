package com.sogeco.fleet.modules.audit;

/**
 * Actions tracees dans le journal d'audit.
 * Liste volontairement restreinte aux operations sensibles :
 * tout tracer reviendrait a ne rien tracer d'exploitable.
 */
public final class AuditAction {

    public static final String LOGIN_SUCCESS        = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE        = "LOGIN_FAILURE";
    public static final String LOGOUT               = "LOGOUT";
    public static final String ACCOUNT_LOCKED       = "ACCOUNT_LOCKED";

    public static final String USER_CREATED         = "USER_CREATED";
    public static final String USER_UPDATED         = "USER_UPDATED";
    public static final String USER_SUSPENDED       = "USER_SUSPENDED";
    public static final String USER_DELETED         = "USER_DELETED";
    public static final String PASSWORD_CHANGED     = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET       = "PASSWORD_RESET";
    public static final String TOTP_ENABLED         = "TOTP_ENABLED";
    public static final String TOTP_DISABLED        = "TOTP_DISABLED";

    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";
    public static final String SETTING_UPDATED      = "SETTING_UPDATED";

    // Utilises a partir du sprint 2
    public static final String AMOUNT_MODIFIED      = "AMOUNT_MODIFIED";
    public static final String ODOMETER_CORRECTED   = "ODOMETER_CORRECTED";
    public static final String ALERT_RESOLVED       = "ALERT_RESOLVED";
    public static final String BONUS_GRANTED        = "BONUS_GRANTED";

    private AuditAction() {
    }
}
