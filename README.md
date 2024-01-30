# JIRA Custom Fields API Script Documentation

## Overview
This script, is designed for use in JIRA Data Center environments and requires the ScriptRunner plugin. It provides a set of API endpoints to perform common operations on custom fields and custom field contexts in JIRA. The script supports single and bulk operations, JSON data handling, and includes robust logging and error handling mechanisms.

## Key Features
- **Add Custom Field Option:** Allows adding a new option to a custom field.
- **Update Custom Field Option:** Enables updating the text of an existing custom field option.
- **Bulk Add Custom Field Options:** Supports adding multiple custom field options in bulk using a JSON body.
- **Sort Custom Field Options:** Facilitates sorting existing options alphabetically.

## Prerequisites
- JIRA Data Center
- ScriptRunner plugin for JIRA

## Usage

### 1. Add Custom Field Option
- **Endpoint:** `POST /custom/customFieldOption`
- **Parameters:**
  - `fieldConfigId` (Long): The configuration ID of the custom field.
  - `value` (String): The value of the new custom field option.
- **CURL Example:**
```
curl -X POST -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOption?fieldConfigId=10222&value=My%20New%20Value"
```




### 2. Update Custom Field Option
- **Endpoint:** `PUT /custom/customFieldOption`
- **Parameters:**
- `optionId` (Long): The ID of the custom field option to update.
- `value` (String): The new value for the custom field option.
- **CURL Example:**
```
curl -X PUT -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOption?optionId=10003&value=Changed%20value"
```



### 3. Bulk Add Custom Field Options
- **Endpoint:** `POST /custom/customFieldOptionBulk`
- **Parameters:**
- `fieldConfigId` (Long): The configuration ID of the custom field.
- JSON Body: A JSON structure containing the options to be added.
- **CURL Example:**
```
curl -X POST -H "Content-Type: application/json" -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOptionBulk?fieldConfigId=10222" -d '{"options":[{"parent":"Parent1","child":"Child1"}, {"parent":"Parent2","child":null}]}'
```



### 4. Sort Custom Field Options
- **Endpoint:** `POST /custom/customFieldOptionSort`
- **Parameters:**
- `fieldConfigId` (Long): The configuration ID of the custom field.
- **CURL Example:**
```
curl -X POST -H 'X-Atlassian-token: no-check' -u <user>:<password> "<base-url>/rest/scriptrunner/latest/custom/customFieldOptionSort?fieldConfigId=10222"
```


## Error Handling
The script includes comprehensive error handling, providing clear messages for issues such as missing or invalid parameters, permission issues, and others.

## Logging
Logging is implemented throughout the script, offering detailed insights during operation execution, which is essential for troubleshooting and monitoring.

---
# Author: Alex Hutanu
*Note: The script is specifically tailored for JIRA Data Center and requires administrative permissions for execution.*
