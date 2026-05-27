<#-- Reusable phone number input; include from login/registration templates -->
<#macro phoneNumberField>
    <div class="${properties.kcFormGroupClass!}">
        <div class="${properties.kcLabelWrapperClass!}">
            <label for="phoneNumber" class="${properties.kcLabelClass!}">${msg("phoneNumberLabel")}</label>
        </div>
        <div class="${properties.kcInputWrapperClass!}">
            <input type="tel"
                   id="phoneNumber"
                   name="phoneNumber"
                   class="${properties.kcInputClass!}"
                   autocomplete="tel"
                   placeholder="${msg("phoneNumberPlaceholder")}"
                   aria-describedby="phoneNumber-help"
                   value="${(phoneNumber!'')}" />
        </div>
        <div id="phoneNumber-help" class="${properties.kcLabelWrapperClass!}">
            <span class="${properties.kcInputHelperTextClass!}">${msg("phoneNumberHelp")}</span>
        </div>
    </div>
</#macro>
