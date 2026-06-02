# Keycloak Universal SMS Authenticator

[![Maven Central](https://img.shields.io/maven-central/v/io.github.shahrear002/keycloak-universal-sms.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.shahrear002/keycloak-universal-sms)

A production-ready, provider-agnostic SMS Authenticator SPI for **Keycloak 17–26 (Quarkus)**. 

This plugin allows you to add SMS-based 2FA / OTP to your Keycloak authentication flows without writing custom Java code for specific SMS gateways (like Twilio, AWS SNS, Infobip, etc.). Instead, you configure your SMS gateway's REST API details directly in the Keycloak Admin Console.

## Features

- **Provider Agnostic**: Works with almost any HTTP/REST-based SMS gateway.
- **Admin Console Configured**: Define URL, Method (GET/POST), Headers, and Payload templates from the UI.
- **Secure & Resilient**: Implements constant-time OTP comparison, configurable Time-To-Live (TTL), and never logs OTP values.
- **Phone Normalisation**: Automatically cleans up user input, strips spaces, and converts local numbers to E.164 format.
- **phoneNumber attribute**: Declarative User Profile validator, registration form action, required action, and collect-phone flow step with i18n (EN / pt-BR).
- **Customisable UI**: Comes with a clean, responsive FreeMarker template (`sms-validation.ftl`) that you can override in your own theme.
- **Zero runtime dependencies**: Uses the JDK HTTP client only; the shaded JAR does not bundle Keycloak, SLF4J, or Jakarta APIs.

### Keycloak version alignment

Compile this project against the **same Keycloak version** as your server (see `keycloak.version` in `pom.xml`, currently **26.0.7**). Deploy `target/keycloak-universal-sms-*.jar` to `providers/`, then run `kc.sh build` before start.

---

## 📦 Installation

### Option 1: Maven Dependency
If you are building a custom Keycloak distribution or want to include this in your own project, you can pull it directly from Maven Central:

```xml
<dependency>
    <groupId>io.github.shahrear002</groupId>
    <artifactId>keycloak-universal-sms</artifactId>
    <version>1.0.2</version>
</dependency>
```

### Option 2: Manual Build

1. **Build the JAR**
   Requires **JDK 21** and Maven (or `make package`, which uses Docker).
   ```bash
   make package
   # or: mvn clean package
   ```
   This produces a shaded JAR at `target/keycloak-universal-sms-1.0.2.jar`.

2. **Deploy to Keycloak**
   Copy the generated JAR into your Keycloak `providers/` directory:
   ```bash
   cp target/keycloak-universal-sms-1.0.2.jar /opt/keycloak/providers/
   ```

3. **Rebuild Keycloak (Quarkus only)**
   For Keycloak 17+, you must run a build step to register the new provider:
   ```bash
   /opt/keycloak/bin/kc.sh build
   ```

4. **Restart Keycloak**
   ```bash
   /opt/keycloak/bin/kc.sh start
   ```

---

## ⚙️ Configuration in Keycloak

### 1. Add the Authenticator to a Flow

1. Go to the Keycloak Admin Console -> **Authentication**.
2. Duplicate the **Browser** flow (call it e.g., "Browser with SMS 2FA").
3. Find the execution step where you want to require SMS (usually after Username/Password).
4. Click **Add execution** and select **Universal SMS OTP**.
5. Set its requirement to **Required** or **Alternative** depending on your needs.
6. Bind this new flow as the default Browser flow for the realm.

### 2. Configure the SMS Gateway

On the "Universal SMS OTP" execution you just added, click the **⚙️ (Config)** icon or **Actions -> Config**.

Fill in the fields based on your SMS Gateway provider:

#### Example 1: Twilio (POST / Form-Encoded)
- **SMS API URL**: `https://api.twilio.com/2010-04-01/Accounts/YOUR_ACCOUNT_SID/Messages.json`
- **HTTP Method**: `POST`
- **Authorization Header Value**: `Basic base64(YOUR_ACCOUNT_SID:YOUR_AUTH_TOKEN)`
- **Payload Template**: `To={phoneNumber}&From=%2B15005550006&Body=Your+Keycloak+verification+code+is+{code}`

#### Example 2: Generic JSON API (POST)
- **SMS API URL**: `https://api.some-gateway.com/v1/send`
- **HTTP Method**: `POST`
- **Authorization Header Value**: `Bearer YOUR_API_KEY`
- **Payload Template**: `{"to":"{phoneNumber}","message":"Your OTP is {code}"}`

#### Example 3: Simple GET Gateway
- **SMS API URL**: `https://sms.example.com/send?to={phoneNumber}&msg=OTP+{code}&key=SECRET_KEY`
- **HTTP Method**: `GET`
- **Authorization Header Value**: *(Leave blank)*
- **Payload Template**: *(Leave blank)*

### 3. Configure the `phoneNumber` User Attribute

The SMS authenticator reads the user attribute `phoneNumber`. This plugin provides validators, registration helpers, required actions, and an optional collection step so numbers are validated and stored in **E.164** form (e.g. `+5511912345678`) before SMS is sent.

#### Declarative User Profile (recommended)

1. Open **Realm settings → User profile** (JSON editor).
2. Add the attribute from `src/main/resources/profile/phone-number-attribute.json` (adjust `defaultCountryCode` as needed):

```json
{
  "name": "phoneNumber",
  "displayName": "${phoneNumberLabel}",
  "validations": {
    "phone-number": {
      "defaultCountryCode": "55"
    }
  },
  "permissions": {
    "view": ["admin", "user"],
    "edit": ["admin", "user"]
  },
  "required": {
    "roles": ["user"]
  }
}
```

The `${phoneNumberLabel}` placeholder is resolved from message bundles (`messages_en.properties`, `messages_pt_BR.properties`, or your theme).

After `kc.sh build` and restart, the custom validator id `phone-number` is available in the User Profile UI.

#### Registration flow

1. **Authentication → Flows → Registration**.
2. Add execution **Registration Phone Number** (provider id `registration-phone-number`) before user creation.
3. Optionally configure **Default Country Code** on the execution (same semantics as the SMS authenticator).

If you use a custom registration template, include the phone field macro from `theme-resources/templates/phone-number-field.ftl`, or rely on the User Profile attribute on the registration form.

#### Required action: Update Phone Number

1. **Authentication → Required actions** — enable **Update Phone Number** (`UPDATE_PHONE_NUMBER`).
2. Assign it to users (manually, via admin API, or through the **Collect Phone Number** authenticator’s `setRequiredActions`).
3. Set `defaultCountryCode` in the required-action provider config (realm JSON / Admin API) if users enter local numbers starting with `0`.

Template: `update-phone-number.ftl`.

#### Browser / custom flow: Collect Phone Number

1. Add execution **Collect Phone Number** (`collect-phone-number`) before **Universal SMS OTP** when users may log in without a stored number.
2. Configure **Default Country Code** on the execution to match the SMS step.

Template: `collect-phone-number.ftl`.

#### Locales

Bundled message keys: English (`messages_en.properties`) and Portuguese Brazil (`messages_pt_BR.properties`). Copy them into your login theme or extend your theme’s `theme.properties` supported locales list.

---

## 🎨 UI & Theme Customisation

The plugin provides default templates: `sms-validation.ftl`, `collect-phone-number.ftl`, `update-phone-number.ftl`, and the reusable macro `phone-number-field.ftl`.

To customise them:
1. Copy files from `src/main/resources/theme-resources/templates/` into your custom Keycloak theme's `login/` folder.
2. Edit the HTML/CSS as needed.
3. Override text by copying keys from `messages_en.properties` and `messages_pt_BR.properties` into your theme's message bundles.

---

## 💻 Developer Guide: Writing a Custom Sender

If you have highly specific needs (e.g., AWS SNS SDK integration, cryptographic signing of requests), you can implement the `SmsSender` interface in Java instead of using the generic HTTP sender.

1. Create a class implementing `com.github.keycloak.sms.SmsSender`.
2. Register it using the Java SPI mechanism by creating `META-INF/services/com.github.keycloak.sms.SmsSender`.
3. Modify `SmsAuthenticator.java` to load your custom SPI implementation instead of instantiating `GenericHttpSmsSender`.

---

## License

Apache License, Version 2.0. See `LICENSE` for details.
