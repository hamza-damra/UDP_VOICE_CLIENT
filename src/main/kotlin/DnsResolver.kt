import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

data class DnsResult(
    val domain: String,
    val resolvedIps: List<String>,
    val isSuccessful: Boolean,
    val errorMessage: String? = null,
    val resolutionTimeMs: Long = 0
)

class DnsResolver {
    
    suspend fun resolveDomain(domain: String): DnsResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Validate domain format first
                if (!isValidDomainName(domain)) {
                    return@withContext DnsResult(
                        domain = domain,
                        resolvedIps = emptyList(),
                        isSuccessful = false,
                        errorMessage = "Invalid domain name format"
                    )
                }
                
                // If it's already an IP address, return it as-is
                if (isIpAddress(domain)) {
                    return@withContext DnsResult(
                        domain = domain,
                        resolvedIps = listOf(domain),
                        isSuccessful = true,
                        resolutionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                
                // Resolve domain to IP addresses
                val addresses = InetAddress.getAllByName(domain)
                val resolvedIps = addresses.map { it.hostAddress }
                val endTime = System.currentTimeMillis()
                
                DnsResult(
                    domain = domain,
                    resolvedIps = resolvedIps,
                    isSuccessful = true,
                    resolutionTimeMs = endTime - startTime
                )
                
            } catch (e: UnknownHostException) {
                DnsResult(
                    domain = domain,
                    resolvedIps = emptyList(),
                    isSuccessful = false,
                    errorMessage = "Domain not found: ${e.message}",
                    resolutionTimeMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                DnsResult(
                    domain = domain,
                    resolvedIps = emptyList(),
                    isSuccessful = false,
                    errorMessage = "DNS resolution failed: ${e.message}",
                    resolutionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }
    
    suspend fun reverseLookup(ipAddress: String): DnsResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                if (!isIpAddress(ipAddress)) {
                    return@withContext DnsResult(
                        domain = ipAddress,
                        resolvedIps = emptyList(),
                        isSuccessful = false,
                        errorMessage = "Invalid IP address format"
                    )
                }
                
                val address = InetAddress.getByName(ipAddress)
                val hostname = address.canonicalHostName
                val endTime = System.currentTimeMillis()
                
                // If hostname equals IP, no reverse DNS record exists
                val resolvedDomain = if (hostname != ipAddress) hostname else null
                
                DnsResult(
                    domain = resolvedDomain ?: "No reverse DNS record",
                    resolvedIps = listOf(ipAddress),
                    isSuccessful = resolvedDomain != null,
                    errorMessage = if (resolvedDomain == null) "No reverse DNS record found" else null,
                    resolutionTimeMs = endTime - startTime
                )
                
            } catch (e: Exception) {
                DnsResult(
                    domain = ipAddress,
                    resolvedIps = emptyList(),
                    isSuccessful = false,
                    errorMessage = "Reverse DNS lookup failed: ${e.message}",
                    resolutionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }
    
    private fun isValidDomainName(domain: String): Boolean {
        if (domain.isEmpty() || domain.length > 253) return false
        
        // Basic domain validation
        val domainPattern = Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$")
        return domainPattern.matches(domain)
    }
    
    private fun isIpAddress(address: String): Boolean {
        if (address.isEmpty()) return false
        
        val parts = address.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
}
