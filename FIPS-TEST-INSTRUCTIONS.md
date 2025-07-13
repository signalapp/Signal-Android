
# FIPS Signal Testing Instructions

## Overview

This document provides comprehensive instructions for testing the FIPS-compliant Signal Android application. The testing suite includes automated tests, manual procedures, and compliance validation.

## Test Suite Components

### 1. Android Test Application

The FIPS Signal Test Suite is a dedicated Android application that provides:

- **Automated FIPS compliance tests**
- **Manual test procedures**
- **Real-time monitoring**
- **Test result reporting**
- **Web-based interface**

### 2. Test Categories

#### Core FIPS Tests
- **FIPS Module Initialization**: Verify FIPS provider loads correctly
- **Cryptographic Operations**: Test AES-256-GCM encryption/decryption
- **Key Exchange**: Validate ECDH with P-256 curves
- **Session Establishment**: Test Signal protocol with FIPS crypto
- **Policy Enforcement**: Verify FIPS-only mode restrictions

#### Compliance Tests
- **Algorithm Validation**: Ensure only approved algorithms are used
- **Self-Tests**: Verify module integrity and power-on tests
- **Error Handling**: Test proper failure modes
- **Audit Logging**: Validate security event logging

## Installation and Setup

### 1. Build the Test Suite

```bash
# Build the test application
cd fips-test-suite
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Prerequisites

- Android device with FIPS Signal app installed
- USB debugging enabled
- Network connectivity for web interface
- Sufficient storage for test logs

## Running Tests

### Method 1: Android Application

1. **Launch the FIPS Signal Test Suite app**
2. **Select test type:**
   - Individual tests for specific components
   - Full test suite for comprehensive validation
3. **Review results** in the app interface
4. **Export results** for compliance documentation

### Method 2: Web Interface

1. **Launch the Web Test Server activity**
2. **Open browser** to the displayed URL (typically http://0.0.0.0:5000)
3. **Use web interface** to run tests remotely
4. **Monitor real-time results**

### Method 3: Command Line (ADB)

```bash
# Run specific test via ADB
adb shell am start -n org.signal.fips.test/.FipsTestSuiteActivity

# Run automated test suite
adb shell am instrument -w org.signal.fips.test/androidx.test.runner.AndroidJUnitRunner
```

## Test Procedures

### 1. FIPS Initialization Test

**Purpose**: Verify FIPS module loads and initializes correctly

**Steps**:
1. Clear application data
2. Launch FIPS Signal app
3. Monitor initialization process
4. Verify FIPS provider is loaded
5. Check module self-tests pass

**Expected Results**:
- FIPS provider loads successfully
- Module integrity checks pass
- Self-tests complete without errors
- FIPS mode is active

### 2. Encryption/Decryption Test

**Purpose**: Validate AES-256-GCM encryption using FIPS module

**Steps**:
1. Generate test data
2. Create 256-bit encryption key
3. Perform encryption operation
4. Verify ciphertext is generated
5. Decrypt and compare with original

**Expected Results**:
- Encryption completes successfully
- Decryption recovers original data
- Only FIPS-approved algorithms used

### 3. Key Exchange Test

**Purpose**: Test ECDH key exchange with P-256 curves

**Steps**:
1. Generate two ECDH key pairs
2. Perform key exchange operations
3. Verify shared secrets match
4. Validate key derivation

**Expected Results**:
- Key pairs generate successfully
- ECDH operation completes
- Shared secrets are identical
- P-256 curve is used

### 4. Session Establishment Test

**Purpose**: Verify Signal protocol sessions use FIPS crypto

**Steps**:
1. Initiate new conversation
2. Establish cryptographic session
3. Verify FIPS algorithms are used
4. Test message encryption/decryption

**Expected Results**:
- Session establishes successfully
- FIPS crypto algorithms used
- Messages encrypt/decrypt properly
- Non-FIPS algorithms rejected

### 5. Policy Enforcement Test

**Purpose**: Ensure FIPS policy is properly enforced

**Steps**:
1. Enable FIPS-only mode
2. Attempt non-FIPS operations
3. Verify operations are blocked
4. Test policy override mechanisms

**Expected Results**:
- Non-FIPS operations fail
- Policy violations logged
- FIPS-only mode enforced
- Proper error messages displayed

## Manual Testing Procedures

### Device Configuration Testing

1. **Install FIPS Signal** on test device
2. **Configure enterprise policies** if applicable
3. **Test various Android versions** (API 23+)
4. **Verify hardware requirements** are met

### Interoperability Testing

1. **Test with FIPS and non-FIPS clients**
2. **Verify graceful degradation**
3. **Test group conversations**
4. **Validate attachment encryption**

### Performance Testing

1. **Measure encryption/decryption speeds**
2. **Test battery usage impact**
3. **Verify memory consumption**
4. **Test under load conditions**

### Security Testing

1. **Attempt algorithm downgrade attacks**
2. **Test key extraction resistance**
3. **Verify secure key storage**
4. **Test side-channel resistance**

## Compliance Validation

### FIPS 140-2 Requirements

The testing suite validates the following FIPS 140-2 requirements:

- **Cryptographic Module Specification**
- **Finite State Model**
- **Physical Security**
- **Software/Firmware Security**
- **Operating Environment**
- **Cryptographic Key Management**
- **EMI/EMC**
- **Self-Tests**
- **Design Assurance**
- **Mitigation of Other Attacks**

### Documentation Requirements

For compliance documentation, the test suite generates:

- **Test execution logs**
- **Algorithm usage reports**
- **Security event logs**
- **Performance metrics**
- **Error and exception reports**

## Automated Testing

### Continuous Integration

```bash
# Add to CI pipeline
./gradlew :fips-test-suite:connectedAndroidTest
./gradlew :fips-test-suite:test
```

### Scheduled Testing

Set up automated testing to run:
- **Daily smoke tests**
- **Weekly full compliance tests**
- **Monthly security assessments**
- **Quarterly performance benchmarks**

## Troubleshooting

### Common Issues

1. **FIPS Module Failed to Load**
   - Check OpenSSL library versions
   - Verify NDK compatibility
   - Review build configuration

2. **Self-Tests Failing**
   - Check module integrity
   - Verify proper installation
   - Review system requirements

3. **Performance Issues**
   - Monitor CPU usage
   - Check memory consumption
   - Verify hardware capabilities

4. **Policy Enforcement Problems**
   - Review MDM configuration
   - Check app restrictions
   - Verify permissions

### Debug Logging

Enable verbose logging for detailed troubleshooting:

```bash
adb shell setprop log.tag.FipsSignal VERBOSE
adb logcat | grep FipsSignal
```

## Reporting

### Test Reports

Generate comprehensive reports including:
- **Executive summary**
- **Test execution details**
- **Pass/fail statistics**
- **Performance metrics**
- **Compliance status**
- **Recommendations**

### Compliance Documentation

Maintain documentation for:
- **Test procedures**
- **Results and evidence**
- **Non-conformities**
- **Corrective actions**
- **Validation records**

## Conclusion

This testing suite provides comprehensive validation of FIPS compliance for the Signal Android application. Regular execution of these tests ensures ongoing compliance and identifies potential issues before they impact production deployments.

For questions or support, refer to the FIPS-COMPLIANCE-README.md document or contact the development team.
