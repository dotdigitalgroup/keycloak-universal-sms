<#import "template.ftl" as layout>
<#import "phone-number-field.ftl" as phone>

<@layout.registrationLayout displayInfo=false; section>
    <#if section = "header">
        ${msg("collectPhoneNumberTitle")}
    <#elseif section = "form">
        <form id="kc-collect-phone-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <@phone.phoneNumberField />
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" value="${msg("doSubmit")}" />
                </div>
            </div>
        </form>
    </#if>
</@layout>
