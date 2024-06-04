package com.asanasoft.common.provider;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

//import io.vertx.ext.auth.oauth2.impl.AccessTokenImpl;

/**
 *
 */
public class MFCUserImpl extends OAuth2TokenImpl implements MFCUser {
    private Logger logger = LoggerFactory.getLogger(MFCUserImpl.class);
    private JsonObject principal = null;
    private String DEFAULT_AUTHORITY = "Default Authority";
    private MFCAuthProvider authProvider = null;

    /*
     * This method compares the user's entitlements (the ACL groups to which the user belongs) to a pipe-delimited
     * list of one or more ACL groups defined (in authorization.properties) for the application functionality
     * of the HTTP request. If the user's entitlements include any one of the app-defined ACL groups,
     * the user is authorized to perform the HTTP request; otherwise not.
     */
    @Override
    public User isAuthorized(String authorities, Handler<AsyncResult<Boolean>> resultHandler) {
        boolean userIsAuthorized = false;
        JsonArray userEntitlements = getPrincipal().getJsonArray("entitlements");
        logger.debug("user's entitlements: " + userEntitlements.toString());
        logger.debug("authorities: " + authorities);
        StringTokenizer tokens = new StringTokenizer(authorities, "|");
        while (tokens.hasMoreTokens()) {
            String authority = tokens.nextToken().trim();
            logger.debug("checking for authority: " + authority);
            if (userEntitlements.contains(authority)) {
                logger.debug("user is authorized");
                userIsAuthorized = true;
                break;
            }
        }
        resultHandler.handle(Future.succeededFuture(Boolean.valueOf(userIsAuthorized)));
        return this;
    }

    // This method is unused but AbstractUser requires it be implemented
    @Override
    protected void doIsPermitted(String s, Handler<AsyncResult<Boolean>> handler) {

    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {
        super.setAuthProvider(authProvider);
        this.authProvider = (MFCAuthProvider)authProvider;
    }

    @Override
    public void writeToBuffer(Buffer buff) {
        byte[] bytes = principal().encode().getBytes(StandardCharsets.UTF_8);
        buff.appendInt(bytes.length);
        buff.appendBytes(bytes);
    }

    @Override
    public JsonObject principal() {
        if (principal == null) {
            principal = new JsonObject();
        }

        if (principal.getJsonArray("entitlements") == null) {
            principal.put("entitlements", new JsonArray());
        }

        return principal;
    }

    
    protected JsonObject getPrincipal() {
        return principal();
    }

    public void copyPrincipal(User otherUser) {
        this.principal = otherUser.principal();
    }

    public String getUsername() {
        return getPrincipal().getString("username");
    }

    public String getEmployeeID() {
        return getPrincipal().getString("employeeID");
    }

    public String getDisplayName() {
        return getPrincipal().getString("displayName");
    }

    public String getFirstName() {
        return getPrincipal().getString("firstName");
    }

    public String getLastName() {
        return getPrincipal().getString("lastName");
    }

    public String getEmail() {
        return getPrincipal().getString("email");
    }

    public JsonArray getEntitlements() {
        return getPrincipal().getJsonArray("entitlements");
    }

    public void setUsername(String username) {
        getPrincipal().put("username", username);
    }

    public void setEmployeeID(String employeeID) {
        getPrincipal().put("employeeID", employeeID);
    }

    public void setDisplayName(String displayName) {
        getPrincipal().put("displayName", displayName);
    }

    public void setFirstName(String firstName) {
        getPrincipal().put("firstName", firstName);
    }

    public void setLastName(String lastName) {
        getPrincipal().put("lastName", lastName);
    }

    public void setEmail(String email) {
        getPrincipal().put("email", email);
    }

    public void addEntitlement(String entitlement) {
        getPrincipal().getJsonArray("entitlements").add(entitlement);
    }

    @Override
    public void setAuthorized(boolean isAutorized) {
        logger.debug("in setAuthorized, isAutorized: " + isAutorized);
        if (isAutorized) {
            addEntitlement(DEFAULT_AUTHORITY);
        }
        else {
            getPrincipal().getJsonArray("entitlements").clear();
        }
    }
}
