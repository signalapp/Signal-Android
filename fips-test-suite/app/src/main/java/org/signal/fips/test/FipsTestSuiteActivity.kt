// DEPRECATED — see proposals/fips-discovery-2026-04-17.md

package org.signal.fips.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.thoughtcrime.securesms.crypto.FipsBridge
import org.thoughtcrime.securesms.crypto.SessionBuilder
import org.thoughtcrime.securesms.crypto.SessionCipher
import java.security.SecureRandom

class FipsTestSuiteActivity : AppCompatActivity() {
    private lateinit var testResultsView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var runTestsButton: Button
    private val testResults = mutableListOf<TestResult>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fips_test_suite)
        
        initializeViews()
        setupTestButtons()
    }
    
    private fun initializeViews() {
        testResultsView = findViewById(R.id.test_results)
        progressBar = findViewById(R.id.progress_bar)
        runTestsButton = findViewById(R.id.run_tests_button)
        
        findViewById<Button>(R.id.export_results_button).setOnClickListener {
            exportTestResults()
        }
    }
    
    private fun setupTestButtons() {
        runTestsButton.setOnClickListener {
            runAllTests()
        }
        
        findViewById<Button>(R.id.test_fips_initialization).setOnClickListener {
            runSingleTest("FIPS Initialization") { testFipsInitialization() }
        }
        
        findViewById<Button>(R.id.test_encryption).setOnClickListener {
            runSingleTest("Encryption/Decryption") { testEncryptionDecryption() }
        }
        
        findViewById<Button>(R.id.test_key_exchange).setOnClickListener {
            runSingleTest("Key Exchange") { testKeyExchange() }
        }
        
        findViewById<Button>(R.id.test_session_establishment).setOnClickListener {
            runSingleTest("Session Establishment") { testSessionEstablishment() }
        }
        
        findViewById<Button>(R.id.test_policy_enforcement).setOnClickListener {
            runSingleTest("Policy Enforcement") { testPolicyEnforcement() }
        }
    }
    
    private fun runAllTests() {
        CoroutineScope(Dispatchers.Main).launch {
            testResults.clear()
            progressBar.visibility = ProgressBar.VISIBLE
            runTestsButton.isEnabled = false
            
            val tests = listOf(
                "FIPS Initialization" to ::testFipsInitialization,
                "Encryption/Decryption" to ::testEncryptionDecryption,
                "Key Exchange" to ::testKeyExchange,
                "Session Establishment" to ::testSessionEstablishment,
                "Policy Enforcement" to ::testPolicyEnforcement,
                "Self-Tests" to ::testSelfTests,
                "Algorithm Validation" to ::testAlgorithmValidation,
                "Error Handling" to ::testErrorHandling
            )
            
            for ((testName, testFunction) in tests) {
                val result = withContext(Dispatchers.IO) {
                    try {
                        testFunction()
                        TestResult(testName, true, "PASSED")
                    } catch (e: Exception) {
                        TestResult(testName, false, "FAILED: ${e.message}")
                    }
                }
                testResults.add(result)
                updateTestResults()
            }
            
            progressBar.visibility = ProgressBar.GONE
            runTestsButton.isEnabled = true
        }
    }
    
    private fun runSingleTest(testName: String, testFunction: suspend () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { testFunction() }
                addTestResult(TestResult(testName, true, "PASSED"))
            } catch (e: Exception) {
                addTestResult(TestResult(testName, false, "FAILED: ${e.message}"))
            }
        }
    }
    
    private suspend fun testFipsInitialization(): Unit = withContext(Dispatchers.IO) {
        val result = FipsBridge.initialize()
        if (!result) {
            throw Exception("FIPS Bridge initialization failed")
        }
        
        // Verify FIPS mode is active
        val fipsStatus = FipsBridge.isFipsMode()
        if (!fipsStatus) {
            throw Exception("FIPS mode not active after initialization")
        }
    }
    
    private suspend fun testEncryptionDecryption(): Unit = withContext(Dispatchers.IO) {
        val testData = "Hello, FIPS World!".toByteArray()
        val key = ByteArray(32) // 256-bit key
        SecureRandom().nextBytes(key)
        
        val iv = ByteArray(12) // GCM IV
        SecureRandom().nextBytes(iv)
        
        // Test AES-256-GCM encryption
        val encrypted = FipsBridge.aesGcmEncrypt(key, iv, testData)
        if (encrypted == null) {
            throw Exception("Encryption failed")
        }
        
        // Test decryption
        val decrypted = FipsBridge.aesGcmDecrypt(key, iv, encrypted)
        if (decrypted == null || !decrypted.contentEquals(testData)) {
            throw Exception("Decryption failed or data mismatch")
        }
    }
    
    private suspend fun testKeyExchange(): Unit = withContext(Dispatchers.IO) {
        // Test ECDH key exchange using P-256
        val keyPair1 = FipsBridge.generateEcdhKeyPair()
        val keyPair2 = FipsBridge.generateEcdhKeyPair()
        
        if (keyPair1 == null || keyPair2 == null) {
            throw Exception("Key pair generation failed")
        }
        
        val sharedSecret1 = FipsBridge.performEcdh(keyPair1.privateKey, keyPair2.publicKey)
        val sharedSecret2 = FipsBridge.performEcdh(keyPair2.privateKey, keyPair1.publicKey)
        
        if (sharedSecret1 == null || sharedSecret2 == null) {
            throw Exception("ECDH operation failed")
        }
        
        if (!sharedSecret1.contentEquals(sharedSecret2)) {
            throw Exception("Shared secrets don't match")
        }
    }
    
    private suspend fun testSessionEstablishment(): Unit = withContext(Dispatchers.IO) {
        // Test Signal protocol session establishment with FIPS crypto
        val recipientId = "test-recipient"
        val deviceId = 1
        
        val sessionBuilder = SessionBuilder()
        val result = sessionBuilder.buildFipsSession(recipientId, deviceId)
        
        if (!result) {
            throw Exception("FIPS session establishment failed")
        }
        
        // Verify session uses FIPS algorithms
        val sessionCipher = SessionCipher()
        val isFipsSession = sessionCipher.isFipsSession(recipientId, deviceId)
        
        if (!isFipsSession) {
            throw Exception("Session is not using FIPS algorithms")
        }
    }
    
    private suspend fun testPolicyEnforcement(): Unit = withContext(Dispatchers.IO) {
        // Test that FIPS policy is properly enforced
        val testMessage = "Test message for FIPS policy"
        
        // Attempt to send message without FIPS session
        try {
            val result = SessionCipher().encryptWithNonFips(testMessage.toByteArray())
            if (result != null) {
                throw Exception("Non-FIPS encryption should be blocked when FIPS is required")
            }
        } catch (e: SecurityException) {
            // Expected behavior - non-FIPS crypto should be blocked
        }
        
        // Verify FIPS-only mode is enforced
        if (!FipsBridge.isFipsOnlyMode()) {
            throw Exception("FIPS-only mode is not enforced")
        }
    }
    
    private suspend fun testSelfTests(): Unit = withContext(Dispatchers.IO) {
        // Run FIPS module self-tests
        val selfTestResult = FipsBridge.runSelfTests()
        if (!selfTestResult) {
            throw Exception("FIPS module self-tests failed")
        }
        
        // Verify module integrity
        val integrityResult = FipsBridge.verifyModuleIntegrity()
        if (!integrityResult) {
            throw Exception("FIPS module integrity verification failed")
        }
    }
    
    private suspend fun testAlgorithmValidation(): Unit = withContext(Dispatchers.IO) {
        // Test that only approved algorithms are used
        val approvedAlgorithms = listOf("AES-256-GCM", "ECDH-P256", "SHA-256")
        
        for (algorithm in approvedAlgorithms) {
            val isApproved = FipsBridge.isAlgorithmApproved(algorithm)
            if (!isApproved) {
                throw Exception("Algorithm $algorithm should be approved but isn't")
            }
        }
        
        // Test that unapproved algorithms are rejected
        val unapprovedAlgorithms = listOf("ChaCha20", "X25519", "MD5")
        
        for (algorithm in unapprovedAlgorithms) {
            val isApproved = FipsBridge.isAlgorithmApproved(algorithm)
            if (isApproved) {
                throw Exception("Algorithm $algorithm should not be approved but is")
            }
        }
    }
    
    private suspend fun testErrorHandling(): Unit = withContext(Dispatchers.IO) {
        // Test proper error handling in FIPS operations
        try {
            // Test with invalid key size
            val invalidKey = ByteArray(16) // 128-bit key instead of 256-bit
            val iv = ByteArray(12)
            val data = "test".toByteArray()
            
            val result = FipsBridge.aesGcmEncrypt(invalidKey, iv, data)
            if (result != null) {
                throw Exception("Encryption with invalid key size should fail")
            }
        } catch (e: SecurityException) {
            // Expected behavior
        }
        
        // Test with corrupted data
        try {
            val key = ByteArray(32)
            val iv = ByteArray(12)
            val corruptedData = ByteArray(100) { 0xFF.toByte() }
            
            val result = FipsBridge.aesGcmDecrypt(key, iv, corruptedData)
            if (result != null) {
                throw Exception("Decryption of corrupted data should fail")
            }
        } catch (e: SecurityException) {
            // Expected behavior
        }
    }
    
    private fun addTestResult(result: TestResult) {
        testResults.add(result)
        updateTestResults()
    }
    
    private fun updateTestResults() {
        val resultsText = buildString {
            appendLine("=== FIPS Signal Test Results ===\n")
            
            var passed = 0
            var failed = 0
            
            for (result in testResults) {
                val status = if (result.passed) {
                    passed++
                    "✓ PASS"
                } else {
                    failed++
                    "✗ FAIL"
                }
                
                appendLine("${result.testName}: $status")
                if (!result.passed) {
                    appendLine("  Error: ${result.message}")
                }
                appendLine()
            }
            
            if (testResults.isNotEmpty()) {
                appendLine("Summary: $passed passed, $failed failed")
                appendLine("Success Rate: ${(passed * 100) / testResults.size}%")
            }
        }
        
        testResultsView.text = resultsText
    }
    
    private fun exportTestResults() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FIPS Signal Test Results")
            putExtra(Intent.EXTRA_TEXT, testResultsView.text.toString())
        }
        startActivity(Intent.createChooser(intent, "Export Test Results"))
    }
    
    data class TestResult(
        val testName: String,
        val passed: Boolean,
        val message: String
    )
}
