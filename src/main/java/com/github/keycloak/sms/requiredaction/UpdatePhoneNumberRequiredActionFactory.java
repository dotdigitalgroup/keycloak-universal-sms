package com.github.keycloak.sms.requiredaction;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
public class UpdatePhoneNumberRequiredActionFactory implements RequiredActionFactory {

    @Override
    public String getId() {
        return UpdatePhoneNumberRequiredAction.PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Update Phone Number";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return UpdatePhoneNumberRequiredAction.getInstance();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }
}
