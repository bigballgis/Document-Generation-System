/**
 * Parameter name validation — must match backend ParameterService NAME_PATTERN
 * (see backend/src/main/java/com/docgen/service/ParameterService.java).
 */
export const PARAMETER_NAME_REGEX = /^[a-zA-Z_][a-zA-Z0-9_-]*$/
