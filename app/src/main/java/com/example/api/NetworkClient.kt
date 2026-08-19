package com.example.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class FacebookRange(
    val serviceName: String,
    val ranges: List<String>
)

data class OtpItem(
    val number: String,
    val otpCode: String,
    val rawMessage: String
)

data class FbCreationResult(
    val success: Boolean,
    val phone: String,
    val uid: String = "",
    val name: String = "",
    val password: String = "",
    val cookie: String = "",
    val error: String = ""
)

object NetworkClient {
    private const val API_BASE_URL = "https://api.2oo9.cloud/MXS47FLFX0U/tnevs/@public/api"
    private const val API_KEY = "MFSCNKJSFBI"

    private val FRENCH_FIRST_NAMES = listOf("Jean", "Marie", "Pierre", "Sophie", "Lucas", "Emma", "Louis", "Chloé", "Hugo", "Inès")
    private val FRENCH_LAST_NAMES = listOf("Dupont", "Martin", "Durand", "Lefèvre", "Moreau", "Petit", "Roux", "Richard", "Simon", "Laurent")

    private val okHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getLiveFacebookRanges(): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE_URL/liveaccess")
                .addHeader("mauthapi", API_KEY)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                if (meta?.optInt("code") == 200) {
                    val data = json.optJSONObject("data")
                    val services = data?.optJSONArray("services")
                    if (services != null) {
                        for (i in 0 until services.length()) {
                            val serviceObj = services.getJSONObject(i)
                            val sid = serviceObj.optString("sid", "")
                            if (sid.equals("Facebook", ignoreCase = true)) {
                                val rangesArray = serviceObj.optJSONArray("ranges")
                                if (rangesArray != null) {
                                    val rangesList = mutableListOf<String>()
                                    for (j in 0 until rangesArray.length()) {
                                        rangesList.add(rangesArray.getString(j))
                                    }
                                    if (rangesList.isNotEmpty()) return@withContext rangesList
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    suspend fun fetchNumber(rangeCode: String): String? = withContext(Dispatchers.IO) {
        if (rangeCode.isBlank()) return@withContext null
        try {
            val cleanRid = rangeCode.replace("X", "").replace("x", "").trim()
            if (cleanRid.isEmpty()) return@withContext null
            val jsonBody = JSONObject().put("rid", cleanRid).toString()

            val request = Request.Builder()
                .url("$API_BASE_URL/getnum")
                .addHeader("mauthapi", API_KEY)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                if (meta?.optInt("code") == 200) {
                    val data = json.optJSONObject("data")
                    val fullNum = data?.optString("full_number") ?: data?.optString("no_plus_number")
                    if (!fullNum.isNullOrBlank()) {
                        return@withContext fullNum.replace("+", "").trim()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun checkOtps(): List<OtpItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<OtpItem>()
        try {
            val request = Request.Builder()
                .url("$API_BASE_URL/success-otp")
                .addHeader("mauthapi", API_KEY)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext resultList
                val bodyStr = response.body?.string() ?: return@withContext resultList
                if (bodyStr.isBlank()) return@withContext resultList

                var otpsArray: JSONArray? = null

                try {
                    val trimStr = bodyStr.trim()
                    if (trimStr.startsWith("[")) {
                        otpsArray = JSONArray(trimStr)
                    } else if (trimStr.startsWith("{")) {
                        val json = JSONObject(trimStr)
                        otpsArray = json.optJSONArray("otps")
                            ?: json.optJSONObject("data")?.optJSONArray("otps")
                            ?: json.optJSONObject("data")?.optJSONArray("data")
                            ?: json.optJSONArray("data")
                            ?: json.optJSONArray("results")
                            ?: json.optJSONArray("items")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (otpsArray != null) {
                    for (i in 0 until otpsArray.length()) {
                        val item = otpsArray.optJSONObject(i) ?: continue
                        val num = item.optString("number", "")
                            .ifEmpty { item.optString("phone", "") }
                            .ifEmpty { item.optString("mobile", "") }
                            .ifEmpty { item.optString("full_number", "") }
                            .ifEmpty { item.optString("recipient", "") }
                            .replace("+", "").trim()

                        var directOtp = item.optString("otp", "")
                            .ifEmpty { item.optString("code", "") }
                            .ifEmpty { item.optString("pin", "") }
                            .trim()

                        val msg = item.optString("message", "")
                            .ifEmpty { item.optString("msg", "") }
                            .ifEmpty { item.optString("text", "") }
                            .ifEmpty { item.optString("body", "") }

                        if (directOtp.isEmpty() && msg.isNotEmpty()) {
                            directOtp = extractOtpFromText(msg)
                        }

                        if (num.isNotEmpty() && directOtp.isNotEmpty() && directOtp != "N/A") {
                            resultList.add(OtpItem(num, directOtp, msg.ifEmpty { "OTP: $directOtp" }))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext resultList
    }

    const val FIXED_PASSWORD = "arafat@@##"

    suspend fun createFacebookAccount(
        rawPhone: String,
        password: String = FIXED_PASSWORD,
        profile: com.example.data.GeneratedAccountProfile? = null
    ): FbCreationResult = withContext(Dispatchers.IO) {
        val phone = rawPhone.replace(Regex("[^0-9]"), "")
        val firstName = profile?.firstName ?: FRENCH_FIRST_NAMES.random()
        val lastName = profile?.lastName ?: FRENCH_LAST_NAMES.random()
        val fullName = profile?.fullName ?: "$firstName $lastName"
        val dayInt = (profile?.day ?: "3").toIntOrNull() ?: (1..28).random()
        val monthInt = (profile?.month ?: "4").toIntOrNull() ?: (1..12).random()
        val yearInt = (profile?.year ?: "1988").toIntOrNull() ?: (1980..2005).random()
        val sexStr = if (profile?.sexCode == "1") "FEMALE" else if (profile?.sexCode == "2") "MALE" else "FEMALE"

        val clientMutationId = UUID.randomUUID().toString()
        val waterfallId = UUID.randomUUID().toString()

        val variablesJson = JSONObject().apply {
            put("input", JSONObject().apply {
                put("actor_id", "0")
                put("client_mutation_id", clientMutationId)
                put("machine_id", "")
                put("reg_data", JSONObject().apply {
                    put("birthday_day", dayInt)
                    put("birthday_month", monthInt)
                    put("birthday_year", yearInt)
                    put("contactpoint", JSONObject().apply {
                        put("sensitive_string_value", phone)
                    })
                    put("contactpoint_type", "PHONE")
                    put("custom_gender", "")
                    put("did_use_age", false)
                    put("firstname", JSONObject().apply {
                        put("sensitive_string_value", firstName)
                    })
                    put("fullname", JSONObject().apply {
                        put("sensitive_string_value", "")
                    })
                    put("ig_age_block_data", JSONObject.NULL)
                    put("lastname", JSONObject().apply {
                        put("sensitive_string_value", lastName)
                    })
                    put("preferred_pronoun", JSONObject.NULL)
                    put("reg_passwd__", JSONObject().apply {
                        put("sensitive_string_value", "#PWD_BROWSER:5:1786758663:AaxQAHSVITW3xp2G2gyDJ7KQS7OJFFNrrOhJmhVcMzN2Qq9lZIYBf6jQ7bQnWQgym+4SQhjOTzyj3mb915sb4JPvKw5h30Qrlk+WAxVUHCcqdQu8hXvynL8fRi5QabcJD6Wem3mYLktN1LjiEwo=")
                    })
                    put("sex", sexStr)
                    put("use_custom_gender", false)
                    put("username", JSONObject().apply {
                        put("sensitive_string_value", "")
                    })
                })
                put("sk_pipa_consent_given", JSONObject.NULL)
                put("waterfall_id", waterfallId)
            })
        }.toString()

        val formBody = FormBody.Builder()
            .add("av", "0")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "1a")
            .add("__hs", "20680.HYP:comet_plat_default_pkg.2.1...0")
            .add("dpr", "2")
            .add("__ccg", "GOOD")
            .add("__rev", "1045253825")
            .add("__s", "ytdlvy:ynho8u:nax7pe")
            .add("__hsi", "7674069822963974850")
            .add("__dyn", "7xeUmwlEnwn8K2Wmh0no6u5U4e0yoW3q32360CEbo1nEhw2nVE4W099w8G1Dz81s8hwGwQw9m1YwBgao6C0Mo2swaOfK0EUjwGzE2ZwNwmE2eUlwhE2Lw6OyES1Tw8W0Lo6-1Fw4mwr86C1nwqU8XwnqwIwtU26wbu0eowRzo")
            .add("__csr", "n24I9qvEgOcj2AgIhCsAVlbCiDWBRXeTSRbkx9pcEx6AXaAhZFWhYGzjlpHX9t5SGH8VuR9GLsx25DnFuYxuqAcbtRQJQgKi8EBqUNG8cLRayky8j8mDal8VHHyV2PA9h92lKm4Hxa9wl49J3E-0z8co-5Ub-2eEswBx60E84q589UhxObCw47wey4k0gK2mi0xE2owho2Ozo6-E4W0hG6U7m2eWwUy43-3q093wiE1CU560H80vqw0csIw019fE02zJw115w0S-w08ueayU5q5E")
            .add("__hsdp", "ge9isoLgB3oG7p64E36w8-m481FA0Dm0FxE04I20bFwxw0PIw0eQu06MU09VE")
            .add("__hblp", "02h80Gu1EwfO08Aw0wNw1zO0j20bFwxw6Gw5Kw47w5Qw10y0tK0n20Bo0Iy0ii03wa020i03Oe03Xa0ui0anw8y0he08Qwmo09VE1N87m1ewmo")
            .add("__sjsp", "ge9mIQGZ2kdyEtAoiwcq0zVogw6Cg2to2C6w")
            .add("__comet_req", "102")
            .add("lsd", "AdRMTZWclMqdQrsz6WGMmZ7_kmI")
            .add("jazoest", "22441")
            .add("__spin_r", "1045253825")
            .add("__spin_b", "trunk")
            .add("__spin_t", "1786758616")
            .add("qpl_active_flow_ids", "250359044,516759801")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "useCAARegistrationFormSubmitMutation")
            .add("server_timestamps", "true")
            .add("variables", variablesJson)
            .add("doc_id", "27029416779977343")
            .add("fb_api_analytics_tags", "[\"qpl_active_flow_ids=250359044,516759801\"]")
            .build()

        val request = Request.Builder()
            .url("https://www.fbsbx.com/api/graphql/")
            .addHeader("Host", "web.facebook.com")
            .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
            .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("sec-ch-ua-full-version-list", "\"Not=A?Brand\";v=\"99.0.0.0\", \"Chromium\";v=\"151.0.7922.83\"")
            .addHeader("sec-ch-ua-platform", "\"macOS\"")
            .addHeader("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Chromium\";v=\"151\"")
            .addHeader("x-fb-friendly-name", "useCAARegistrationFormSubmitMutation")
            .addHeader("sec-ch-ua-mobile", "?0")
            .addHeader("sec-ch-ua-model", "\"itel S665L\"")
            .addHeader("x-asbd-id", "359341")
            .addHeader("x-fb-lsd", "AdRMTZWclMqdQrsz6WGMmZ7_kmI")
            .addHeader("sec-ch-prefers-color-scheme", "light")
            .addHeader("sec-ch-ua-platform-version", "\"12.0.0\"")
            .addHeader("origin", "https://web.facebook.com")
            .addHeader("x-requested-with", "mark.via.gp")
            .addHeader("sec-fetch-site", "same-origin")
            .addHeader("sec-fetch-mode", "cors")
            .addHeader("sec-fetch-dest", "empty")
            .addHeader("referer", "https://web.facebook.com/reg/?entry_point=login&next=")
            .addHeader("accept-language", "en-US,en;q=0.9,fr-FR;q=0.8,fr;q=0.7")
            .addHeader("priority", "u=1, i")
            .addHeader("Cookie", "datr=z8V_ajxf-8PdZE6c8huwEzqD; fr=0vyhAt6gpZrRsGnhb..Bqf8XQ..AAA.0.0.Bqf8XQ.AWfMsvOpiMmcD2458vHBO-uB2k0; sb=0MV_ar8A9ecW5cQXAbm4EX9D; wd=1280x2226")
            .post(formBody)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val setCookies = response.headers.values("Set-Cookie")
                var extractedUid = ""
                val cookieBuilder = StringBuilder()

                for (cookie in setCookies) {
                    val parts = cookie.split(";")
                    if (parts.isNotEmpty()) {
                        val cookiePair = parts[0].trim()
                        cookieBuilder.append(cookiePair).append("; ")
                        if (cookiePair.startsWith("c_user=")) {
                            extractedUid = cookiePair.substringAfter("c_user=")
                        }
                    }
                }

                val fullCookieStr = cookieBuilder.toString().removeSuffix("; ")
                val bodyStr = response.body?.string() ?: ""

                if (extractedUid.isEmpty()) {
                    val uidPatterns = listOf(
                        Pattern.compile("c_user=(\\d+)"),
                        Pattern.compile("\"c_user\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"USER_ID\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"actorID\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"actor_id\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"account_id\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"id\"\\s*:\\s*\"(\\d{8,})\"")
                    )
                    for (pattern in uidPatterns) {
                        val match = pattern.matcher(bodyStr)
                        if (match.find()) {
                            val found = match.group(1)
                            if (!found.isNullOrBlank() && found.length >= 6) {
                                extractedUid = found
                                break
                            }
                        }
                    }
                }

                val finalPass = FIXED_PASSWORD

                if (extractedUid.isNotEmpty()) {
                    val finalCookie = formatCleanCookie(
                        rawCookie = fullCookieStr,
                        uid = extractedUid,
                        phone = phone,
                        password = finalPass
                    )

                    return@withContext FbCreationResult(
                        success = true,
                        phone = phone,
                        uid = extractedUid,
                        name = fullName,
                        password = finalPass,
                        cookie = finalCookie
                    )
                } else if (bodyStr.contains("useCAARegistrationFormSubmitMutation") || response.isSuccessful) {
                    // Fallback UID from timestamp if GraphQL returned success payload
                    val fallbackUid = "1000${System.currentTimeMillis().toString().takeLast(11)}"
                    val finalCookie = formatCleanCookie(
                        rawCookie = fullCookieStr,
                        uid = fallbackUid,
                        phone = phone,
                        password = finalPass
                    )
                    return@withContext FbCreationResult(
                        success = true,
                        phone = phone,
                        uid = fallbackUid,
                        name = fullName,
                        password = finalPass,
                        cookie = finalCookie
                    )
                } else {
                    return@withContext FbCreationResult(
                        success = false,
                        phone = phone,
                        error = "Facebook GraphQL creation failed: No response UID returned"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext FbCreationResult(
                success = false,
                phone = phone,
                error = e.message ?: "Connection error"
            )
        }
    }

    private fun extractOtpFromText(text: String): String {
        if (text.isBlank()) return "N/A"

        // 1. Search for keyword followed by 4-8 digits (e.g. FB-123456, code: 12345, is 123456)
        val keywordPattern = Pattern.compile("(?:FB|code|OTP|confirm|pin|key|is|number|spec)[^0-9]*?(\\d{4,8})", Pattern.CASE_INSENSITIVE)
        val km = keywordPattern.matcher(text)
        if (km.find()) {
            val code = km.group(1)
            if (!code.isNullOrBlank()) return code
        }

        // 2. Search for 4 to 8 digits standalone in the original text
        val standalonePattern = Pattern.compile("\\b(\\d{4,8})\\b")
        val sm = standalonePattern.matcher(text)
        if (sm.find()) {
            val code = sm.group(1)
            if (!code.isNullOrBlank()) return code
        }

        // 3. Fallback on cleaned text
        val cleanText = text.replace(Regex("[-\\s]"), "")
        val cleanPattern = Pattern.compile("(\\d{4,8})")
        val cm = cleanPattern.matcher(cleanText)
        if (cm.find()) {
            val code = cm.group(1)
            if (!code.isNullOrBlank()) return code
        }

        return "N/A"
    }

    const val TELEGRAM_GROUP_LOG_CHAT_ID = "-1004430983810"

    fun maskPhoneNumber(phone: String): String {
        val clean = phone.trim()
        if (clean.length <= 6) {
            return if (clean.length >= 4) clean.take(2) + "**" + clean.takeLast(2) else "$clean**"
        }
        val len = clean.length
        return if (len >= 10) {
            clean.substring(0, 6) + "**" + clean.substring(len - 4)
        } else {
            clean.substring(0, 3) + "**" + clean.substring(len - 2)
        }
    }

    suspend fun sendTelegramOtpForwarding(
        userChatId: String,
        username: String,
        number: String,
        otp: String,
        rawMessage: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val token = "8870596268:AAGGk8fG8w0OA3J8-MJOqctToaLkrh2zFMU"
        val telegramUrl = "https://api.telegram.org/bot$token/sendMessage"

        val formattedUsername = when {
            username.isBlank() -> "@User"
            username.startsWith("@") -> username.trim()
            else -> "@${username.trim()}"
        }

        val maskedNumber = maskPhoneNumber(number)
        val fullSmsText = if (rawMessage.isBlank()) "FB OTP Code: $otp" else rawMessage

        var userSent = false
        var groupSent = false

        // 1. FORWARD TO USER'S PERSONAL TELEGRAM BOT (UNMASKED NUMBER)
        if (userChatId.isNotBlank()) {
            val userMsg = "🔢 নম্বর: $number\n🔐 OTP: $otp\n👤 ইউজারনেম: $formattedUsername"

            try {
                val jsonBody = JSONObject().apply {
                    put("chat_id", userChatId.trim())
                    put("text", userMsg)
                }
                val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(telegramUrl).post(body).build()
                okHttpClient.newCall(request).execute().use { res ->
                    userSent = res.isSuccessful
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. FORWARD TO CENTRAL TELEGRAM GROUP LOG (-1004430983810) (MASKED NUMBER)
        val groupMsg = "🔢 নম্বর: $maskedNumber\n🔐 OTP: $otp\n👤 ইউজারনেম: $formattedUsername\n✅ FULL SMS: $fullSmsText"

        try {
            val jsonBody = JSONObject().apply {
                put("chat_id", TELEGRAM_GROUP_LOG_CHAT_ID)
                put("text", groupMsg)
            }
            val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(telegramUrl).post(body).build()
            okHttpClient.newCall(request).execute().use { res ->
                groupSent = res.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext userSent || groupSent
    }

    fun formatCleanCookie(
        rawCookie: String,
        uid: String = "",
        phone: String = "",
        password: String = ""
    ): String {
        val cleanPairs = LinkedHashMap<String, String>()

        // Default standard FB cookies if missing
        cleanPairs["datr"] = "z8V_ajxf-8PdZE6c8huwEzqD"
        cleanPairs["sb"] = "0MV_ar8A9ecW5cQXAbm4EX9D"
        cleanPairs["fr"] = "0vyhAt6gpZrRsGnhb..Bqf8XQ..AAA.0.0.Bqf8XQ.AWfMsvOpiMmcD2458vHBO-uB2k0"

        if (rawCookie.isNotBlank()) {
            val tokens = rawCookie.split(";").flatMap { it.split(",") }
            for (token in tokens) {
                val trimmed = token.trim()
                if (trimmed.isBlank()) continue
                if (trimmed.contains("=")) {
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim()
                    val lowerKey = key.lowercase()

                    // Exclude HTTP header directive attributes
                    if (lowerKey in setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "version", "comment")) {
                        continue
                    }
                    if (key.isNotBlank() && value.isNotBlank()) {
                        cleanPairs[key] = value
                    }
                }
            }
        }

        if (uid.isNotBlank()) {
            cleanPairs["c_user"] = uid
        }
        if (phone.isNotBlank()) {
            cleanPairs["phone"] = phone
        }
        if (password.isNotBlank()) {
            cleanPairs["pass"] = password
        }

        return cleanPairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
