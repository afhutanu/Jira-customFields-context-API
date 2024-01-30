import groovy.json.JsonSlurper
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.customfields.MultipleSettableCustomFieldType
import com.atlassian.jira.issue.fields.config.FieldConfig
import com.atlassian.jira.issue.fields.config.manager.FieldConfigManager
import com.atlassian.jira.permission.GlobalPermissionKey
import com.atlassian.sal.api.ApplicationProperties
import com.atlassian.sal.api.UrlMode
import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import groovy.json.JsonOutput
import groovy.transform.BaseScript
import org.apache.commons.lang3.StringUtils

import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response

import static javax.ws.rs.core.Response.Status.BAD_REQUEST

@BaseScript CustomEndpointDelegate delegate

def fieldConfigManager = ComponentAccessor.getComponent(FieldConfigManager)
def optionsManager = ComponentAccessor.optionsManager
def applicationProperties = ComponentAccessor.getOSGiComponentInstanceOfType(ApplicationProperties)
def authenticationContext = ComponentAccessor.jiraAuthenticationContext
def permissionManager = ComponentAccessor.globalPermissionManager

Closure<FieldConfig> getFieldConfig = { Long fieldConfigId ->
    def fieldConfig = fieldConfigManager.getFieldConfig(fieldConfigId)
    if (!fieldConfig) {
        throw new Exception(authenticationContext.i18nHelper.getText('Cannot find this fieldConfig'))
    }

    if (!permissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, authenticationContext.getLoggedInUser())) {
        throw new Exception('Requires admin permissions')
    }

    fieldConfig
}

Closure<FieldConfig> getFieldConfigAndValidateForAddOrUpdate = { Long fieldConfigId, String optionValue ->
    if (!optionValue) {
        throw new Exception(authenticationContext.i18nHelper.getText('admin.options.empty.name'))
    }

    if (optionValue.size() > 255) {
        throw new Exception(authenticationContext.i18nHelper.getText('admin.options.too.long', optionValue))
    }

    def fieldConfig = getFieldConfig(fieldConfigId)

    def customFieldType = fieldConfig.customField.customFieldType
    if (customFieldType !instanceof MultipleSettableCustomFieldType) {
        throw new Exception(authenticationContext.i18nHelper.getText('admin.errors.customfields.cannot.set.options', "'$customFieldType'"))
    }

    def options = optionsManager.getOptions(fieldConfig)

    if (options*.value*.toLowerCase().contains(optionValue.toLowerCase())) {
        throw new Exception(authenticationContext.i18nHelper.getText('admin.errors.customfields.value.already.exists'))
    }

    return fieldConfig
}

/*
    Add custom field option.

    The fieldConfigId can be retrieved from the URL when editing existing custom field options, with the name fieldConfigId.

    For example to create a new value named "My New Value":
    curl -X POST  -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOption?fieldConfigId=10222&value=My%20New%20Value"
*/
customFieldOption(
    httpMethod: 'POST'
) { MultivaluedMap queryParams, String body ->
    def fieldConfigId = queryParams.getFirst('fieldConfigId') as Long
    def optionValue = StringUtils.stripToNull(queryParams.getFirst('value') as String)
    def parentId = queryParams.getFirst('parentId') as Long

    def parentOption = parentId ? parentId : null

    try {
        def fieldConfig = getFieldConfigAndValidateForAddOrUpdate(fieldConfigId, optionValue)

        def options = optionsManager.getOptions(fieldConfig)
        def createdOption = optionsManager.createOption(fieldConfig, parentOption, options.size(), optionValue)


        Response.temporaryRedirect(URI.create(applicationProperties.getBaseUrl(UrlMode.ABSOLUTE) + '/rest/api/2/customFieldOption/' + createdOption.optionId)).build()
    }
    catch (any) {
        return Response.status(BAD_REQUEST).entity(JsonOutput.toJson(any.message)).build()
    }
}

/*
    Update text of existing custom field option.

    The optionId can be retrieved from the URL when editing the text of a custom field option, or via the existing REST API.

    For example to rename the option with ID: 10003 to "Changed value":
    curl -X PUT -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOption?optionId=10003&value=Changed%20value"
*/
customFieldOption(
    httpMethod: 'PUT'
) { MultivaluedMap queryParams ->
    def optionId = queryParams.getFirst('optionId') as Long
    def optionValue = queryParams.getFirst('value') as String

    def option = optionsManager.findByOptionId(optionId)
    if (!option) {
        return Response.status(BAD_REQUEST).entity(JsonOutput.toJson('Missing option')).build()
    }

    try {
        getFieldConfigAndValidateForAddOrUpdate(option.relatedCustomField.id, optionValue)
        optionsManager.setValue(option, optionValue)
        Response.temporaryRedirect(URI.create(applicationProperties.getBaseUrl(UrlMode.ABSOLUTE) + '/rest/api/2/customFieldOption/' + option.optionId)).build()
    }
    catch (any) {
        return Response.status(BAD_REQUEST).entity(JsonOutput.toJson(any.message)).build()
    }
}

/*
    Bulk add custom field options using JSON body.

    The fieldConfigId is required as a query parameter.
    A JSON body should be provided with structure:
    {
        "options": [
            {"parent": "Parent Value 1", "child": "Child Value 1"},
            {"parent": "Parent Value 2", "child": null},
            ...
        ]
    }

    Example to create new values with JSON body:
    curl -X POST -H "Content-Type: application/json" -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOptionBulk?fieldConfigId=10222" -d '{"options":[{"parent":"Parent1","child":"Child1"}, {"parent":"Parent2","child":null}]}'
*/

customFieldOptionBulk(
    httpMethod: 'POST'
) { MultivaluedMap queryParams, String body ->
    def fieldConfigId = queryParams.getFirst('fieldConfigId') as Long
    def jsonSlurper = new JsonSlurper()
    def parsedBody = body ? jsonSlurper.parseText(body) : null

    try {
        def fieldConfig = getFieldConfig(fieldConfigId)

         if (parsedBody instanceof Map && parsedBody.containsKey("options")) {
            parsedBody.options.each { Map option ->
                if (!(option instanceof Map)) {
                    throw new IllegalArgumentException("Each option must be a JSON object")
                }
                log.warn("Processing option: $option")

                def parentValue = option.get("parent")?.toString()
                def childValue = option.get("child")?.toString()
                log.warn("Child value: $childValue")

                //log.warn("Processing ${parentValue} and ${childValue}")

                try {
                // Validate parent value
                    getFieldConfigAndValidateForAddOrUpdate(fieldConfigId, parentValue)
                }

                catch (any) {
                    return Response.status(BAD_REQUEST).entity(JsonOutput.toJson(any.message)).build()
                }
                // Find or create parent option
                def parentOption = optionsManager.getOptions(fieldConfig).find { it.value == parentValue } ?: optionsManager.createOption(fieldConfig, null, null, parentValue)
                log.warn("Child value: $childValue, Type: ${childValue?.getClass()}, Truthiness: ${childValue ? true : false}")

                if (childValue) {
                    try {
                        log.warn("Processing child option for parent: ${parentValue}")

                        // Your logic to handle child value...

                    } catch (Exception e) {
                        log.error("Error processing child option for parent: ${parentValue}", e)
                    }
                } else {
                    log.warn("No child value to process for parent: ${parentValue}")
                }
                log.warn("==================================================")

                // If child value is provided, create child option
                if (childValue) {
                    getFieldConfigAndValidateForAddOrUpdate(fieldConfigId, childValue)
                    log.warn("====================== PROCESSING CHILD VALUE ======================")
                    log.warn("Trying to create child for ${parentOption}")
                    log.warn("====================== ID: ${parentOption.getOptionId()} ======================")
                    optionsManager.createOption(fieldConfig, parentOption.getOptionId(), null, childValue)

                }
                log.warn("==================================================")
            }
        }

        Response.noContent().build()
    } catch (any) {
        return Response.status(BAD_REQUEST).entity(JsonOutput.toJson(any.message)).build()
    }
}

/*
 * Sort existing options alphabetically
 *
 * Example:
 * curl -X POST  -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOptionSort?fieldConfigId=10222"
 */
customFieldOptionSort(
    httpMethod: 'POST'
) { MultivaluedMap queryParams ->
    def fieldConfigId = queryParams.getFirst('fieldConfigId') as Long
    def fieldConfig = getFieldConfig(fieldConfigId)
    def options = optionsManager.getOptions(fieldConfig)

    options.sortOptionsByValue(null)
    Response.noContent().build()
}