package com.example.data.remote

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MontySaasSession(
    val accessToken: String,
    val userId: String,
    val agencyId: String,
    val role: String
)

data class MontySaasAiReply(
    val responseText: String,
    val executionMode: String,
    val cacheHit: Boolean
)

data class MontySaasCheckoutSession(
    val orderId: String,
    val checkoutSessionId: String,
    val checkoutUrl: String,
    val amountEuro: Long,
    val status: String
)

data class MontySaasOrderVerification(
    val orderId: String,
    val paid: Boolean,
    val status: String,
    val dossierId: String
)

/**
 * Client HTTP léger assurant la convergence entre l'application Android Monty
 * et le backend SaaS FastAPI (`POST /api/v1/auth/firebase-exchange`, Monty AI Core,
 * Monty Price Index et paiement Stripe serveur du Pack Vendeur).
 */
class MontySaasApiClient(
    private val baseUrl: String = DEFAULT_SAAS_BASE_URL,
    private val auth: FirebaseAuth = Firebase.auth,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    @Volatile
    private var cachedSession: MontySaasSession? = null

    private fun normalizedBaseUrl(): String = baseUrl.trimEnd('/')

    private fun isSafePublicHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://10.0.2.2", ignoreCase = true) ||
            trimmed.startsWith("http://localhost", ignoreCase = true)
    }

    suspend fun exchangeFirebaseSession(
        preferredRole: String = "BUYER",
        agencyId: String = "agency-france-reseau"
    ): Result<MontySaasSession> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizedBaseUrl()
            require(isSafePublicHttpUrl(base)) { "URL SaaS non sécurisée : HTTPS requis." }
            val user = auth.currentUser
                ?: throw IllegalStateException("Session Google/Firebase requise pour synchroniser le backend SaaS.")
            val idToken = user.getIdToken(false).await().token
                ?: throw IllegalStateException("Impossible de récupérer le jeton Firebase ID.")

            val payload = JSONObject().apply {
                put("id_token", idToken)
                put("preferred_role", preferredRole)
                put("agency_id", agencyId)
            }
            val request = Request.Builder()
                .url("$base/api/v1/auth/firebase-exchange")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Échec échange session SaaS (HTTP ${resp.code}).")
                }
                val json = JSONObject(raw)
                val session = MontySaasSession(
                    accessToken = json.optString("access_token"),
                    userId = json.optString("user_id"),
                    agencyId = json.optString("agency_id"),
                    role = json.optString("role", "BUYER")
                )
                require(session.accessToken.isNotBlank()) { "Jeton SaaS absent de la réponse serveur." }
                cachedSession = session
                session
            }
        }
    }

    private suspend fun requireBearerToken(): String {
        val current = cachedSession
        if (current != null && current.accessToken.isNotBlank()) {
            return current.accessToken
        }
        return exchangeFirebaseSession().getOrThrow().accessToken
    }

    suspend fun chatWithMontyAi(
        prompt: String,
        taskType: String = "CONVERSATION",
        confidentialMode: Boolean = false,
        contextPayload: Map<String, Any> = emptyMap()
    ): Result<MontySaasAiReply> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireBearerToken()
            val payload = JSONObject().apply {
                put("prompt", prompt.take(3800))
                put("task_type", taskType)
                put("confidential_mode", confidentialMode)
                put("context_payload", JSONObject(contextPayload))
            }
            val request = Request.Builder()
                .url("${normalizedBaseUrl()}/api/v1/monty-ai/chat")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Monty AI Core indisponible (HTTP ${resp.code}).")
                }
                val json = JSONObject(raw)
                MontySaasAiReply(
                    responseText = json.optString("response_text"),
                    executionMode = json.optString("execution_mode", "TOOL_ASSISTED_OFFLINE"),
                    cacheHit = json.optBoolean("cache_hit", false)
                )
            }
        }
    }

    suspend fun createSellerPackCheckout(
        dossierId: String,
        propertyId: String,
        orderId: String,
        buyerEmail: String,
        successUrl: String = "${normalizedBaseUrl()}/portail?seller_pack=success",
        cancelUrl: String = "${normalizedBaseUrl()}/portail?seller_pack=cancel"
    ): Result<MontySaasCheckoutSession> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizedBaseUrl()
            require(isSafePublicHttpUrl(base)) { "URL SaaS non sécurisée." }
            val token = cachedSession?.accessToken
                ?: exchangeFirebaseSession().getOrNull()?.accessToken
            val payload = JSONObject().apply {
                put("dossier_id", dossierId)
                put("property_id", propertyId)
                put("order_id", orderId)
                put("buyer_email", buyerEmail.trim())
                put("success_url", successUrl)
                put("cancel_url", cancelUrl)
            }
            val builder = Request.Builder()
                .url("$base/api/v1/billing/seller-pack/checkout")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            httpClient.newCall(builder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Checkout Stripe Pack Vendeur indisponible (HTTP ${resp.code}).")
                }
                val json = JSONObject(raw)
                MontySaasCheckoutSession(
                    orderId = json.optString("order_id", orderId),
                    checkoutSessionId = json.optString("checkout_session_id"),
                    checkoutUrl = json.optString("checkout_url"),
                    amountEuro = json.optLong("amount_euro", 29L),
                    status = json.optString("status", "PENDING_PAYMENT")
                )
            }
        }
    }

    suspend fun verifySellerPackOrder(
        orderId: String,
        sessionId: String = ""
    ): Result<MontySaasOrderVerification> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizedBaseUrl()
            require(isSafePublicHttpUrl(base)) { "URL SaaS non sécurisée." }
            val encodedOrder = URLEncoder.encode(orderId, "UTF-8")
            val encodedSession = URLEncoder.encode(sessionId, "UTF-8")
            val request = Request.Builder()
                .url("$base/api/v1/billing/seller-pack/verify?order_id=$encodedOrder&session_id=$encodedSession")
                .get()
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Vérification paiement Stripe échouée (HTTP ${resp.code}).")
                }
                val json = JSONObject(raw)
                MontySaasOrderVerification(
                    orderId = json.optString("order_id", orderId),
                    paid = json.optBoolean("paid", false),
                    status = json.optString("status", "PENDING_PAYMENT"),
                    dossierId = json.optString("dossier_id")
                )
            }
        }
    }

    companion object {
        const val DEFAULT_SAAS_BASE_URL: String = "https://monty-saas-api.onrender.com"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
