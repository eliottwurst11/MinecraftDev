/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2023 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.platform.mixin.config.inspection

import com.demonwav.mcdev.platform.mixin.config.reference.ConfigProperty
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonNullLiteral
import com.intellij.json.psi.JsonNumberLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonValue
import com.intellij.psi.CommonClassNames
import com.intellij.psi.CommonClassNames.JAVA_LANG_BYTE
import com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER
import com.intellij.psi.CommonClassNames.JAVA_LANG_DOUBLE
import com.intellij.psi.CommonClassNames.JAVA_LANG_FLOAT
import com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import com.intellij.psi.CommonClassNames.JAVA_LANG_LONG
import com.intellij.psi.CommonClassNames.JAVA_LANG_SHORT
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiUtil

class ConfigValueInspection : MixinConfigInspection() {

    override fun getStaticDescription() = "Reports invalid values in Mixin configuration files."

    override fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor = Visitor(holder)

    private class Visitor(private val holder: ProblemsHolder) : JsonElementVisitor() {

        override fun visitProperty(property: JsonProperty) {
            val value = property.value ?: return
            val targetField =
                ConfigProperty.resolveReference(property.nameElement as? JsonStringLiteral ?: return) as? PsiField
                    ?: return
            checkValue(targetField.type, value)
        }

        private fun checkValue(type: PsiType, value: JsonValue) {
            val valid = when (type) {
                PsiTypes.booleanType() -> value is JsonBooleanLiteral
                PsiTypes.byteType(), PsiTypes.doubleType(), PsiTypes.floatType(), PsiTypes.intType(),
                PsiTypes.longType(), PsiTypes.shortType() ->
                    value is JsonNumberLiteral
                is PsiArrayType -> checkArray(type.componentType, value)
                else -> checkObject(type, value)
            }

            if (!valid) {
                holder.registerProblem(value, "Expected value of type '${type.presentableText}'")
            }
        }

        private fun checkArray(childType: PsiType, value: JsonValue): Boolean {
            if (value !is JsonArray) {
                holder.registerProblem(value, "Array expected")
                return true
            }

            for (child in value.valueList) {
                checkValue(childType, child)
            }
            return true
        }

        private fun checkObject(type: PsiType, value: JsonValue): Boolean {
            if (type !is PsiClassType) {
                return true // Idk, it's fine I guess
            }

            if (type.equalsToText(JAVA_LANG_STRING)) {
                return value is JsonStringLiteral
            }

            if (type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
                return value is JsonBooleanLiteral || value is JsonNullLiteral
            }

            if (qualifiedNumberNames.any(type::equalsToText)) {
                return value is JsonNumberLiteral || value is JsonNullLiteral
            }

            PsiUtil.extractIterableTypeParameter(type, true)?.let { return checkArray(it, value) }
            return value is JsonObject
        }

        private val qualifiedNumberNames = listOf(
            JAVA_LANG_BYTE,
            JAVA_LANG_CHARACTER,
            JAVA_LANG_DOUBLE,
            JAVA_LANG_FLOAT,
            JAVA_LANG_INTEGER,
            JAVA_LANG_LONG,
            JAVA_LANG_SHORT,
        )
    }
}
