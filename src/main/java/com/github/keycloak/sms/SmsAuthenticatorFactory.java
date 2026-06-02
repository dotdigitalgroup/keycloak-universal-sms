package com.github.keycloak.sms;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory that registers the SMS Authenticator with Keycloak and exposes all
 * configuration properties to the Admin Console.
 *
 * <p>Configuration fields appear on the authenticator execution card in the
 * realm's Authentication → Flows page when the administrator clicks
 * <em>Actions → Config</em>.
 */
public class SmsAuthenticatorFactory implements AuthenticatorFactory {

    // -------------------------------------------------------------------------
    // Provider ID — must match the value in META-INF/services file
    // -------------------------------------------------------------------------

    public static final String PROVIDER_ID = "universal-sms-authenticator";

    // -------------------------------------------------------------------------
    // Configuration property keys (used by SmsAuthenticator)
    // -------------------------------------------------------------------------

    public static final String CONF_API_URL     = "smsApiUrl";
    public static final String CONF_HTTP_METHOD = "smsHttpMethod";
    public static final String CONF_AUTH_HEADER = "smsAuthHeader";
    public static final String CONF_PAYLOAD_TPL = "smsPayloadTemplate";
    public static final String CONF_OTP_LENGTH  = "otpLength";
    public static final String CONF_OTP_TTL     = "otpTtlMinutes";
    public static final String CONF_COUNTRY_CODE= "defaultCountryCode";

    // -------------------------------------------------------------------------
    // Singleton authenticator (stateless)
    // -------------------------------------------------------------------------

    private static final SmsAuthenticator INSTANCE = new SmsAuthenticator();

    // -------------------------------------------------------------------------
    // Config property definitions
    // -------------------------------------------------------------------------

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        CONFIG_PROPERTIES = ProviderConfigurationBuilder.create()

            // --- Gateway URL -------------------------------------------------
            .property()
                .name(CONF_API_URL)
                .label("SMS API URL")
                .helpText(
                    "Full URL of the SMS gateway endpoint. " +
                    "For GET-based gateways you may embed {phoneNumber} and {code} " +
                    "directly in the URL. " +
                    "Example: https://api.example.com/sms/send")
                .type(ProviderConfigProperty.STRING_TYPE)
                .required(true)
                .add()

            // --- HTTP Method -------------------------------------------------
            .property()
                .name(CONF_HTTP_METHOD)
                .label("HTTP Method")
                .helpText("HTTP method to use when calling the gateway. Most modern APIs use POST.")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("POST", "GET")
                .defaultValue("POST")
                .add()

            // --- Auth Header -------------------------------------------------
            .property()
                .name(CONF_AUTH_HEADER)
                .label("Authorization Header Value")
                .helpText(
                    "Value of the Authorization header sent with each request. " +
                    "Examples: 'Bearer YOUR_TOKEN', 'Basic dXNlcjpwYXNz'. " +
                    "Leave blank if the gateway does not require authentication.")
                .type(ProviderConfigProperty.PASSWORD)
                .secret(true)
                .add()

            // --- Payload template -------------------------------------------
            .property()
                .name(CONF_PAYLOAD_TPL)
                .label("Payload Template")
                .helpText(
                    "Request body template. Use {phoneNumber} and {code} as placeholders. " +
                    "JSON example: {\"to\":\"{phoneNumber}\",\"message\":\"Your OTP is {code}\"}. " +
                    "Form-encoded example: To={phoneNumber}&Body=OTP+{code}. " +
                    "Leave blank for GET requests (put params in the URL instead).")
                .type(ProviderConfigProperty.TEXT_TYPE)
                .add()

            // --- OTP Length -------------------------------------------------
            .property()
                .name(CONF_OTP_LENGTH)
                .label("OTP Length (digits)")
                .helpText("Number of digits in the generated one-time password. Default: 6.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("6")
                .add()

            // --- OTP TTL ----------------------------------------------------
            .property()
                .name(CONF_OTP_TTL)
                .label("OTP Time-to-Live (minutes)")
                .helpText("How many minutes the OTP remains valid after being sent. Default: 5.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5")
                .add()

            // --- Default Country Code ---------------------------------------
            .property()
                .name(CONF_COUNTRY_CODE)
                .label("Default Country Code")
                .helpText(
                    "Country calling code (digits only, no '+') to prepend when the user " +
                    "enters a local number starting with 0. " +
                    "Example: 880 for Bangladesh, 1 for USA/Canada. " +
                    "Leave blank to disable automatic country-code injection.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("")
                .add()

            .build();
    }

    // -------------------------------------------------------------------------
    // AuthenticatorFactory implementation
    // -------------------------------------------------------------------------

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Universal SMS OTP";
    }

    @Override
    public String getReferenceCategory() {
        return "otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED,
        };
    }

    @Override
    public String getHelpText() {
        return "Sends a one-time password via any SMS gateway configured through the " +
               "Admin Console. Supports GET and POST gateways with a customisable " +
               "payload template. Compatible with Keycloak 17–26 (Quarkus-based).";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return INSTANCE;
    }

    @Override
    public void init(Config.Scope config) {
        // nothing to initialise at server startup
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing post-init
    }

    @Override
    public void close() {
        // nothing to close
    }
}
