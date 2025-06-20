/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.reflect.KClass

/**
 * Use this custom rule to ignore exceptions thrown by another rule.
 *
 * @param innerRule The rule to wrap.
 * @param exceptionsToIgnore The exceptions to ignore.
 */
class CatchExceptionsRule(
    private val innerRule: TestRule,
    private vararg val exceptionsToIgnore: KClass<out Throwable>
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    innerRule.apply(base, description).evaluate()
                } catch (e: Throwable) {
                    val shouldIgnore = exceptionsToIgnore.any { it.isInstance(e) }
                    if (shouldIgnore)
                        base.evaluate()
                    else
                        throw e
                }
            }
        }
    }
}
