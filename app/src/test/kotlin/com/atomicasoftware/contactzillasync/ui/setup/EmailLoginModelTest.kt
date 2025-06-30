/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.ui.setup

import org.junit.Test
import org.junit.Assert.*

class EmailLoginModelTest {

    private fun createModel(): EmailLoginModel {
        return EmailLoginModel(LoginInfo())
    }

    @Test
    fun `test valid contactzilla email address`() {
        val model = createModel()
        model.setEmail("user@contactzilla.app")
        model.setPassword("password")
        
        val uiState = model.uiState
        assertTrue("Valid contactzilla email should be accepted", uiState.isValidDomain)
        assertFalse("Should not show domain error for valid email", uiState.showDomainError)
        assertTrue("Should be able to continue with valid email and password", uiState.canContinue)
    }

    @Test
    fun `test invalid email domain`() {
        val model = createModel()
        model.setEmail("user@example.com")
        
        val uiState = model.uiState
        assertFalse("Invalid domain should not be accepted", uiState.isValidDomain)
        assertTrue("Should show domain error for invalid domain", uiState.showDomainError)
        assertFalse("Should not be able to continue with invalid domain", uiState.canContinue)
    }

    @Test
    fun `test case insensitive domain validation`() {
        val model = createModel()
        model.setEmail("user@CONTACTZILLA.APP")
        model.setPassword("password")
        
        val uiState = model.uiState
        assertTrue("Domain validation should be case insensitive", uiState.isValidDomain)
        assertFalse("Should not show domain error for valid email with different case", uiState.showDomainError)
        assertTrue("Should be able to continue with valid email in different case", uiState.canContinue)
    }

    @Test
    fun `test empty email`() {
        val model = createModel()
        model.setEmail("")
        
        val uiState = model.uiState
        assertFalse("Empty email should show no domain error", uiState.showDomainError)
        assertFalse("Empty email should show no general email error", uiState.showGeneralEmailError)
        assertFalse("Should not be able to continue with empty email", uiState.canContinue)
    }

    @Test
    fun `test malformed email with valid domain`() {
        val model = createModel()
        model.setEmail("invalid-email@contactzilla.app")
        model.setPassword("password")
        
        val uiState = model.uiState
        assertTrue("Domain should be valid", uiState.isValidDomain)
        // Note: This test depends on how DavUtils.toURIorNull() validates email format
        // The current implementation might accept this as valid
    }

    @Test
    fun `test subdomain rejection`() {
        val model = createModel()
        model.setEmail("user@subdomain.contactzilla.app")
        
        val uiState = model.uiState
        assertFalse("Subdomain should not be accepted", uiState.isValidDomain)
        assertTrue("Should show domain error for subdomain", uiState.showDomainError)
        assertFalse("Should not be able to continue with subdomain", uiState.canContinue)
    }

    @Test
    fun `test similar domain rejection`() {
        val model = createModel()
        model.setEmail("user@contactzilla.app.evil.com")
        
        val uiState = model.uiState
        assertFalse("Similar domain should not be accepted", uiState.isValidDomain)
        assertTrue("Should show domain error for similar domain", uiState.showDomainError)
        assertFalse("Should not be able to continue with similar domain", uiState.canContinue)
    }
} 