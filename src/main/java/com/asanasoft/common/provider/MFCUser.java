package com.asanasoft.common.provider;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;

public interface MFCUser extends AccessToken {

    static MFCUser create() {
        return new MFCUserImpl();
    }

    void copyPrincipal(User otherUser);

    String getUsername();

    String getEmployeeID();

    String getDisplayName();

    String getFirstName();

    String getLastName();

    String getEmail();

    JsonArray getEntitlements();

    void setUsername(String username);

    void setEmployeeID(String employeeID);

    void setDisplayName(String displayName);

    void setFirstName(String firstName);

    void setLastName(String lastName);

    void setEmail(String email);

    void addEntitlement(String entitlement);

    void setAuthorized(boolean isAutorized);
}