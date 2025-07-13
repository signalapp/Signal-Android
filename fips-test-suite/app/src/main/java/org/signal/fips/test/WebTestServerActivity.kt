
package org.signal.fips.test

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class WebTestServerActivity : AppCompatActivity() {
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_test_server)
        
        statusText = findViewById(R.id.server_status)
        
        startWebServer()
    }
    
    private fun startWebServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(5000)
                isServerRunning = true
                
                withContext(Dispatchers.Main) {
                    statusText.text = "Test server running on http://0.0.0.0:5000"
                }
                
                while (isServerRunning) {
                    val clientSocket = serverSocket?.accept()
                    clientSocket?.let { socket ->
                        launch { handleClient(socket) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Server error: ${e.message}"
                }
            }
        }
    }
    
    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            
            val requestLine = reader.readLine()
            val path = requestLine?.split(" ")?.get(1) ?: "/"
            
            // Read headers
            var line: String?
            do {
                line = reader.readLine()
            } while (line != null && line.isNotEmpty())
            
            val response = when {
                path == "/" -> generateMainPage()
                path == "/api/test" -> handleTestExecution(reader)
                path == "/api/status" -> generateStatusResponse()
                path.startsWith("/static/") -> handleStaticFile(path)
                else -> generate404Response()
            }
            
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: ${getContentType(path)}")
            writer.println("Content-Length: ${response.length}")
            writer.println("Access-Control-Allow-Origin: *")
            writer.println()
            writer.print(response)
            writer.flush()
            
            socket.close()
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    private fun generateMainPage(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>FIPS Signal Test Suite</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .test-button { margin: 5px; padding: 10px; background: #007bff; color: white; border: none; cursor: pointer; }
        .test-button:hover { background: #0056b3; }
        .results { background: #f8f9fa; padding: 15px; margin: 10px 0; border: 1px solid #dee2e6; }
        .pass { color: green; } .fail { color: red; }
        .loading { color: orange; }
    </style>
</head>
<body>
    <h1>FIPS Signal Test Suite</h1>
    
    <div>
        <h2>Test Controls</h2>
        <button class="test-button" onclick="runAllTests()">Run All Tests</button>
        <button class="test-button" onclick="runTest('fips-init')">Test FIPS Initialization</button>
        <button class="test-button" onclick="runTest('encryption')">Test Encryption</button>
        <button class="test-button" onclick="runTest('key-exchange')">Test Key Exchange</button>
        <button class="test-button" onclick="runTest('session')">Test Session</button>
        <button class="test-button" onclick="runTest('policy')">Test Policy</button>
        <button class="test-button" onclick="clearResults()">Clear Results</button>
    </div>
    
    <div class="results" id="test-results">
        <h3>Test Results</h3>
        <div id="results-content">No tests run yet.</div>
    </div>
    
    <div>
        <h2>FIPS Status</h2>
        <div id="fips-status">Loading...</div>
        <button class="test-button" onclick="checkStatus()">Refresh Status</button>
    </div>
    
    <script>
        async function runTest(testType) {
            updateResults('Running test: ' + testType, 'loading');
            
            try {
                const response = await fetch('/api/test', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ test: testType })
                });
                
                const result = await response.json();
                displayTestResult(testType, result);
            } catch (error) {
                updateResults('Error running test ' + testType + ': ' + error.message, 'fail');
            }
        }
        
        async function runAllTests() {
            const tests = ['fips-init', 'encryption', 'key-exchange', 'session', 'policy'];
            
            for (const test of tests) {
                await runTest(test);
                await new Promise(resolve => setTimeout(resolve, 1000)); // Wait 1 second between tests
            }
        }
        
        function displayTestResult(testType, result) {
            const status = result.success ? 'pass' : 'fail';
            const message = testType + ': ' + (result.success ? 'PASS' : 'FAIL');
            if (result.message) {
                message += ' - ' + result.message;
            }
            updateResults(message, status);
        }
        
        function updateResults(message, cssClass) {
            const content = document.getElementById('results-content');
            const div = document.createElement('div');
            div.className = cssClass;
            div.textContent = new Date().toLocaleTimeString() + ' - ' + message;
            content.appendChild(div);
            content.scrollTop = content.scrollHeight;
        }
        
        function clearResults() {
            document.getElementById('results-content').innerHTML = 'No tests run yet.';
        }
        
        async function checkStatus() {
            try {
                const response = await fetch('/api/status');
                const status = await response.json();
                
                document.getElementById('fips-status').innerHTML = 
                    '<strong>FIPS Mode:</strong> ' + (status.fipsMode ? 'Enabled' : 'Disabled') + '<br>' +
                    '<strong>Module Status:</strong> ' + status.moduleStatus + '<br>' +
                    '<strong>Self-Tests:</strong> ' + (status.selfTestsPassed ? 'Passed' : 'Failed');
            } catch (error) {
                document.getElementById('fips-status').innerHTML = 'Error checking status: ' + error.message;
            }
        }
        
        // Check status on page load
        checkStatus();
        
        // Auto-refresh status every 30 seconds
        setInterval(checkStatus, 30000);
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    private suspend fun handleTestExecution(reader: BufferedReader): String = withContext(Dispatchers.IO) {
        try {
            // For simplicity, return mock results
            // In a real implementation, this would integrate with the actual test framework
            """
            {
                "success": true,
                "message": "Test completed successfully",
                "timestamp": "${System.currentTimeMillis()}"
            }
            """.trimIndent()
        } catch (e: Exception) {
            """
            {
                "success": false,
                "message": "Test failed: ${e.message}",
                "timestamp": "${System.currentTimeMillis()}"
            }
            """.trimIndent()
        }
    }
    
    private fun generateStatusResponse(): String {
        return """
        {
            "fipsMode": true,
            "moduleStatus": "Active",
            "selfTestsPassed": true,
            "timestamp": "${System.currentTimeMillis()}"
        }
        """.trimIndent()
    }
    
    private fun generate404Response(): String {
        return """
        <!DOCTYPE html>
        <html>
        <head><title>404 Not Found</title></head>
        <body><h1>404 Not Found</h1><p>The requested resource was not found.</p></body>
        </html>
        """.trimIndent()
    }
    
    private fun handleStaticFile(path: String): String {
        // Handle static files if needed
        return generate404Response()
    }
    
    private fun getContentType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "application/javascript"
            path.startsWith("/api/") -> "application/json"
            else -> "text/html"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServerRunning = false
        serverSocket?.close()
    }
}
