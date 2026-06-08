/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.loader.plugins

import io.github.tonyzhye.beancount.core.*

/**
 * Commodity attribute validation plugin.
 * Based on beancount.plugins.commodity_attr.
 *
 * Asserts that all Commodity directives have particular attributes
 * and that their values are part of a set of enum values.
 *
 * Configuration format: a map of attribute name to list of valid values,
 * e.g. "{'sector': ['Technology', 'Financials'], 'name': null}"
 *
 * If the list of valid values is null, only presence of the attribute is checked.
 */
class CommodityAttrPlugin(private val config: Map<String, Set<String>?>) {

    /**
     * Validate commodity attributes.
     */
    fun transform(entries: List<Directive>, options: Options): Pair<List<Directive>, List<BeancountError>> {
        val errors = mutableListOf<BeancountError>()

        for (entry in entries) {
            if (entry !is Commodity) continue

            for ((attr, validValues) in config) {
                val value = entry.meta[attr]

                if (value == null) {
                    errors.add(
                        LoadError(
                            source = entry.meta,
                            message = "Missing attribute '$attr' for Commodity directive ${entry.currency}",
                            entry = entry
                        )
                    )
                    continue
                }

                if (validValues != null) {
                    val valueStr = value.toString()
                    if (valueStr !in validValues) {
                        errors.add(
                            LoadError(
                                source = entry.meta,
                                message = "Invalid value '$valueStr' for attribute '$attr', " +
                                    "Commodity directive ${entry.currency}; " +
                                    "valid options: ${validValues.joinToString(", ")}",
                                entry = entry
                            )
                        )
                    }
                }
            }
        }

        return entries to errors
    }

    companion object {
        /**
         * Parse configuration string.
         * Expected format: key:value1,value2;key2:value3
         * Use "null" or empty value list to only check presence.
         */
        fun parseConfig(configStr: String): CommodityAttrPlugin {
            val config = mutableMapOf<String, Set<String>?>()

            if (configStr.isBlank()) {
                return CommodityAttrPlugin(emptyMap())
            }

            // Simple format: "sector:Technology,Financials;name"
            val entries = configStr.split(";")
            for (entry in entries) {
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) continue

                val parts = trimmed.split(":", limit = 2)
                val attr = parts[0].trim()

                if (parts.size == 1) {
                    // Only attribute name, no valid values
                    config[attr] = null
                } else {
                    val values = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    config[attr] = if (values.isEmpty()) null else values
                }
            }

            return CommodityAttrPlugin(config)
        }
    }
}
