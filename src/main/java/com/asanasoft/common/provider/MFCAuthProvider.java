package com.asanasoft.common.provider;


import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */

public class MFCAuthProvider extends OAuth2AuthProviderImpl {
    private Logger logger = LoggerFactory.getLogger(MFCAuthProvider.class);
    private WebClient  webClient    = null;
    private OAuth2Auth oAuth2AuthDelegate = null;
    private String LDAP_SERVICE                 = null;
    private String LDAP_SERVICE_ID_PATH         = null;
    private String LDAP_SERVICE_USERNAME_PATH   = null;

    public MFCAuthProvider(Vertx vertx, OAuth2ClientOptions config) {
        super(vertx, config);
    }

    /**
     * This method is called by the authenticating handler. This implementation encompasses
     * both the OAuth2Provider and the normal AuthProvider. It detects when it has to
     * authenticate via SSO if the OAuth2 delegate is not NULL. This delegate is only
     * instanciated when we call the second constructor.
     *
     * @param jsonObject
     * @param handler
     */
    @Override
    public void authenticate(JsonObject jsonObject, Handler<AsyncResult<User>> handler) {
        logger.debug("In authenticate...");
        /**
         * If the delegate is NOT null, then that means we are using OAuth2 authentication,
         * so let the delegate handle it...
         */
        if (oAuth2AuthDelegate != null) {
            logger.debug("Authenticating via OAuth2 delegate...");
            super.authenticate(jsonObject, authenticationResult -> {
                logger.debug("Back from Authenticating via delegate...");
                Future<User> result = Future.future();

                if (authenticationResult.failed()) {
                    logger.debug("Authenticating via delegate failed...");

                    /**
                     * If SSO failed, then allow this AuthProvider to handle
                     * authentication via a login page or a Basic Challenge.
                     */
                    //this.oAuth2AuthDelegate = null;   ==> commented out 1/23/2018; was killing all users when one user failed authentication
                    result.fail(authenticationResult.cause());
                    handler.handle(result);
                }
                else {
                    logger.debug("Authenticating via delegate succeeded...");

                    AccessToken token = (AccessToken)authenticationResult.result();

                    logger.debug("AccessToken = " + token.toString());

                    token.fetch("/userinfo", tokenResult -> {
                        logger.debug("Fetched user information...");

                        if (tokenResult.failed()) {
                            result.fail(tokenResult.cause());
                            handler.handle(result);
                        }
                        else {
                            JsonObject userObject = tokenResult.result().jsonObject();

                            logger.debug("UserObject = " + userObject.encodePrettily());

                            String employeeID = userObject.getString("user_name");

                            getLDAPUser("http://" + getLDAP_SERVICE() + getLDAP_SERVICE_ID_PATH() + employeeID, ldapResult -> {
                                logger.debug("ldapResult = " + ldapResult.result().encodePrettily());

                                if (ldapResult.failed()) {
                                    result.fail(ldapResult.cause());
                                }
                                else {
                                    MFCUser user = MFCUser.create();
                                    user.principal().getMap().putAll(ldapResult.result().getMap());
                                    user.principal().put("employeeID", employeeID);
                                    logger.debug("user.principal = " +  user.principal().encodePrettily());
                                    result.complete(user);
                                }

                                handler.handle(result);
                            });
                        }
                    });
                }
            });
        }
        else {
            logger.debug("Authenticating WITHOUT OAuth2 delegate...");
            String username = jsonObject.getString("username");
            String password = jsonObject.getString("password");
            String pathAndUserIdentifier = null;

            // Determine whether username/password combination is legitimate.
            // If so, populate user object with EmployeeID from LDAP and return success. (Caller will store user in session object.)
            // Otherwise, return failure (and no user object).

            pathAndUserIdentifier = LDAP_SERVICE_USERNAME_PATH + username;
            System.out.println("MFCAuthProvider - calling HTTP client for " + pathAndUserIdentifier);

            getLDAPUser(pathAndUserIdentifier, ldapResult -> {
                Future<User> result = Future.future();

                if (ldapResult.failed()) {
                    result.fail(ldapResult.cause());
                }
                else {
                    MFCUser user = MFCUser.create();
                    user.principal().getMap().putAll(ldapResult.result().getMap());
                    user.principal().put("employeeID", username);
                    result.complete(user);
                }

                handler.handle(result);
            });
        }
    }

    protected void setupLDAP() {
        setLDAP_SERVICE(Environment.getInstance().getString("LDAP_SERVICE"));
        setLDAP_SERVICE_ID_PATH(Environment.getInstance().getString("LDAP_SERVICE_ID_PATH"));
        setLDAP_SERVICE_USERNAME_PATH(Environment.getInstance().getString("LDAP_SERVICE_USERNAME_PATH"));
    }

    protected void getLDAPUser(String ldapUri, Handler<AsyncResult<JsonObject>> handler) {
        logger.debug("calling LDAP service for " + ldapUri);

        getWebClient().getAbs(ldapUri)
                .as(BodyCodec.jsonObject())
                .send(asyncResponse -> {
                    Future<JsonObject> result = Future.future();

                    if (asyncResponse.succeeded()) {
                        HttpResponse<JsonObject> response = asyncResponse.result();
                        // Create user object and stored LDAP contents (name, entitlements, etc.) in it
                        JsonObject userLDAP = response.body();
                        JsonObject principal = new JsonObject();

                        principal.put("username"    ,userLDAP.getString("Username"));
                        principal.put("displayName" ,userLDAP.getString("DisplayName"));
                        principal.put("firstName"   ,userLDAP.getString("FirstName"));
                        principal.put("lastName"    ,userLDAP.getString("LastName"));
                        principal.put("email"       ,userLDAP.getString("Email"));

                        JsonArray entitlements = new JsonArray();
                        JsonArray entitlementsArray = userLDAP.getJsonArray("Entitlements");

                        for (Object entitlement : entitlementsArray) {
                            if (entitlement.toString().contains("PMAR")) {
                                entitlements.add(entitlement.toString());
                            }
                        }

                        principal.put("entitlements", entitlements);

                        result.complete(principal);
                    }
                    else {
                        logger.error("There was a failure getting LDAP record:" + asyncResponse.cause().getMessage());
                        result.fail(asyncResponse.cause());
                    }

                    handler.handle(result);
                });
    }

    protected WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.create(this.getVertx());
            setupLDAP();
        }

        return webClient;
    }

    public String getLDAP_SERVICE() {
        return LDAP_SERVICE;
    }

    public void setLDAP_SERVICE(String ldap_service) {
        this.LDAP_SERVICE = ldap_service;
    }

    public String getLDAP_SERVICE_USERNAME_PATH() {
        return LDAP_SERVICE_USERNAME_PATH;
    }

    public void setLDAP_SERVICE_USERNAME_PATH(String ldap_service_username_path) {
        this.LDAP_SERVICE_USERNAME_PATH = ldap_service_username_path;
    }

    public String getLDAP_SERVICE_ID_PATH() {
        return LDAP_SERVICE_ID_PATH;
    }

    public void setLDAP_SERVICE_ID_PATH(String ldap_service_id_path) {
        this.LDAP_SERVICE_ID_PATH = ldap_service_id_path;
    }
}