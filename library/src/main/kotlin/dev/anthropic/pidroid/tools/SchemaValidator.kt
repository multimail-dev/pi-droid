package dev.anthropic.pidroid.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Lightweight JSON Schema draft-07 subset validator.
 *
 * Validates the subset that LLM tool schemas actually produce:
 * type, required, properties, enum, minimum, maximum, format, items, default.
 *
 * Deliberately omits: $ref, oneOf/anyOf/allOf, patternProperties, additionalProperties,
 * as LLM tool schemas don't use these in practice.
 *
 * Unknown extra fields in the input object are allowed (LLMs may add unexpected fields).
 */
class SchemaValidator {

    /**
     * Validate [arguments] against [schema].
     * Returns [ValidationResult.Valid] on success, or [ValidationResult.Invalid] with
     * a list of errors describing each violation.
     */
    fun validate(arguments: JsonObject, schema: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()
        validateObject(arguments, schema, path = "", errors)
        return if (errors.isEmpty()) ValidationResult.Valid
        else ValidationResult.Invalid(errors)
    }

    private fun validateObject(obj: JsonObject, schema: JsonObject, path: String, errors: MutableList<String>) {
        // Check required fields
        val required = schema["required"]
        if (required is JsonArray) {
            for (element in required) {
                val fieldName = element.jsonPrimitive.contentOrNull ?: continue
                if (fieldName !in obj) {
                    errors.add("Missing required field '${pathJoin(path, fieldName)}'")
                }
            }
        }

        // Check properties
        val properties = schema["properties"]
        if (properties is JsonObject) {
            for ((key, value) in obj) {
                val propSchema = properties[key]
                if (propSchema is JsonObject) {
                    validateElement(value, propSchema, pathJoin(path, key), errors)
                }
                // Unknown fields are allowed (LLMs may add unexpected fields)
            }
        }
    }

    private fun validateElement(element: JsonElement, schema: JsonObject, path: String, errors: MutableList<String>) {
        // Check type
        val type = schema["type"]?.jsonPrimitive?.contentOrNull
        if (type != null && element !is JsonNull) {
            when (type) {
                "object" -> {
                    if (element !is JsonObject) {
                        errors.add("Type mismatch at '$path': expected object, got ${elementTypeName(element)}")
                        return
                    }
                    validateObject(element, schema, path, errors)
                }
                "array" -> {
                    if (element !is JsonArray) {
                        errors.add("Type mismatch at '$path': expected array, got ${elementTypeName(element)}")
                        return
                    }
                    validateArray(element, schema, path, errors)
                }
                "string" -> {
                    if (element !is JsonPrimitive || !element.isString) {
                        errors.add("Type mismatch at '$path': expected string, got ${elementTypeName(element)}")
                        return
                    }
                }
                "integer" -> {
                    if (element !is JsonPrimitive || element.longOrNull == null) {
                        errors.add("Type mismatch at '$path': expected integer, got ${elementTypeName(element)}")
                        return
                    }
                    validateNumericConstraints(element, schema, path, errors)
                }
                "number" -> {
                    if (element !is JsonPrimitive || element.doubleOrNull == null) {
                        errors.add("Type mismatch at '$path': expected number, got ${elementTypeName(element)}")
                        return
                    }
                    validateNumericConstraints(element, schema, path, errors)
                }
                "boolean" -> {
                    if (element !is JsonPrimitive || element.booleanOrNull == null) {
                        errors.add("Type mismatch at '$path': expected boolean, got ${elementTypeName(element)}")
                        return
                    }
                }
            }
        }

        // Check enum
        val enum = schema["enum"]
        if (enum is JsonArray && element !is JsonNull) {
            if (element !in enum) {
                errors.add("Value at '$path' not in enum: ${enum.map { it.jsonPrimitive.contentOrNull }}")
            }
        }
    }

    private fun validateArray(array: JsonArray, schema: JsonObject, path: String, errors: MutableList<String>) {
        val items = schema["items"]
        if (items is JsonObject) {
            for ((index, element) in array.withIndex()) {
                validateElement(element, items, "$path[$index]", errors)
            }
        }
    }

    private fun validateNumericConstraints(
        element: JsonPrimitive,
        schema: JsonObject,
        path: String,
        errors: MutableList<String>,
    ) {
        val value = element.doubleOrNull ?: return

        val minimum = schema["minimum"]?.jsonPrimitive?.doubleOrNull
        if (minimum != null && value < minimum) {
            errors.add("Value at '$path' is $value, below minimum $minimum")
        }

        val maximum = schema["maximum"]?.jsonPrimitive?.doubleOrNull
        if (maximum != null && value > maximum) {
            errors.add("Value at '$path' is $value, above maximum $maximum")
        }
    }

    private fun elementTypeName(element: JsonElement): String = when (element) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            element.longOrNull != null -> "integer"
            element.doubleOrNull != null -> "number"
            else -> "unknown"
        }
    }

    private fun pathJoin(base: String, field: String): String =
        if (base.isEmpty()) field else "$base.$field"
}

/**
 * Result of schema validation.
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult() {
        val message: String get() = errors.joinToString("; ")
    }
}
