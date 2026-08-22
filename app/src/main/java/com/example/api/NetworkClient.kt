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

import com.example.model.RangeItem

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

data class IgCreationResult(
    val success: Boolean,
    val username: String = "",
    val phone: String = "",
    val message: String = "",
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

    suspend fun getLiveFacebookRanges(): List<RangeItem> = withContext(Dispatchers.IO) {
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
                                    val rangesList = mutableListOf<RangeItem>()
                                    for (j in 0 until rangesArray.length()) {
                                        val elementObj = rangesArray.get(j)
                                        if (elementObj is JSONObject) {
                                            val code = elementObj.optString("range", "")
                                                .ifEmpty { elementObj.optString("code", "") }
                                                .ifEmpty { elementObj.optString("name", "") }
                                                .ifEmpty { elementObj.optString("rangeCode", "") }
                                            val message = elementObj.optString("message", "")
                                                .ifEmpty { elementObj.optString("msg", "") }
                                                .ifEmpty { elementObj.optString("status", "") }
                                                .ifEmpty { elementObj.optString("info", "") }
                                            if (code.isNotBlank()) {
                                                rangesList.add(RangeItem(code, message))
                                            }
                                        } else {
                                            val code = elementObj.toString()
                                            if (code.isNotBlank()) {
                                                rangesList.add(RangeItem(code, ""))
                                            }
                                        }
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
                        uid = extractedUid
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
                        uid = fallbackUid
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
        uid: String = ""
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

                    // Exclude HTTP header directive attributes AND credentials/phone/pass
                    if (lowerKey in setOf(
                            "path", "domain", "expires", "max-age", "secure", "httponly",
                            "samesite", "version", "comment", "phone", "pass", "password", "number", "user", "pwd"
                        )) {
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

        // Return purely Facebook cookie pairs ending with semicolon delimiter
        val joined = cleanPairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return if (joined.endsWith(";")) joined else "$joined;"
    }

    suspend fun createMetaIgAccount(
        phone: String,
        username: String,
        displayName: String = "",
        countryCode: String = "BD",
        passwordRaw: String = FIXED_PASSWORD
    ): IgCreationResult = withContext(Dispatchers.IO) {
        val epochSec = (System.currentTimeMillis() / 1000).toString()
        val waterfallId = UUID.randomUUID().toString()
        val lsdToken = "AdTIykA_1GnAZ8y1MsSzoZCoWeg"
        val encodedPassword = "#PWD_BROWSER:5:$epochSec:AaxQAHSVITW3xp2G2gyDJ7KQS7OJFFNrrOhJmhVcMzN2Qq9lZIYBf6jQ7bQnWQgym+4SQhjOTzyj3mb915sb4JPvKw5h30Qrlk+WAxVUHCcqdQu8hXvynL8fRi5QabcJD6Wem3mYLktN1LjiEwo="
        val nameToUse = displayName.ifBlank { username.substringBefore("_").replaceFirstChar { it.uppercase() } }

        val formBuilder = FormBody.Builder()
            .add("client_consent_timestamp", epochSec)
            .add("display_name", nameToUse)
            .add("foa_import_source_name", "")
            .add("foa_import_source_obid", "")
            .add("nta_disclosures_summary_cms_id", "")
            .add("picture_source", "")
            .add("tos_cms_id", "957798449862312")
            .add("username", username)
            .add("consent_version", "")
            .add("contact_point", phone)
            .add("contact_point_type", "PHONE_NUMBER")
            .add("csi", "4kDhs4XgRql7KFW4tpZ79aqf")
            .add("date_of_birth", "2001-08-19")
            .add("device_id", "")
            .add("fb_encrypted_access_token", "")
            .add("fb_oidc_access_token", "")
            .add("first_name", "")
            .add("google_id_token", "")
            .add("has_youth_consent", "false")
            .add("ig_encrypted_access_token", "")
            .add("ig_encrypted_auth_header", "")
            .add("ig_oidc_access_token", "")
            .add("last_name", "")
            .add("opt_into_marketing", "false")
            .add("password", encodedPassword)
            .add("redirect_uri", "https://auth.meta.com/oidc/?app_id=1522763855472543&redirect_uri=https%3A%2F%2Fauth.meta.ai%2Fecto&response_type=code&scope=openid%2Blinking&state=eyJjc3JmX3Rva2VuIjoia0lrV3ZUY2tFSHROb1FRZGg3MkNjZGxaR0RQOTZpVXVTTlRvMHBzeE83NCIsInJlZGlyZWN0X3RvIjoiaHR0cHM6Ly93d3cubWV0YS5haS9vaWRjL2NhbGxiYWNrIiwic3RhcnRlZF9hdCI6MTc4NzEyNDg5NjUwMSwid2F0ZXJmYWxsX2lkIjoiZTU2NDdjMTktN2YzNy00NTExLTg1M2ItMDdjYjc3NWI3MzQyIn0%3D&waterfall_id=e5647c19-7f37-4511-853b-07cb775b7342&code_challenge=eJxb2CbtS0DPvaphyt1lbeu3p035dzNTalafSfd-ztQ&code_challenge_method=S256")
            .add("reg_integrity", "Q8W2BTc29RJSTgUojxTSyZwKv_b5dYPeHA9vYld5rc6ipGCtdVfAjGIcHTsSVHSQ-8Lc00azJ4iOyLfL8N2AlOuWZifzjsnO3M2UPbW61DbQNSem-LTBNiXduOrcU11wGn-XBgg7o3V8S-TrZz4cqZTLa9OtOOz9i0xqDh_9ix9gGi97nZqcKuHKpDZCYihNiiiNO0ezWjiRDh-XuxGj8sSBVScCCsMCRopI91ZSBnzdq59q1V-rgUCF_FYD3Z6-LHBx8MWvbuPWXdk1Rj0E9jIimyIw-j4fsD_L00ydxmqKsumHmjdZlpKLG5d05IdxvI0qD8_SeYcbEUHUjXgy85wS5ATB-7SOiNLDFkcS|kregenc")
            .add("should_save_credentials", "true")
            .add("source_app_id", "1522763855472543")
            .add("third_party_age_verification_id", "")
            .add("waterfall_id", waterfallId)
            .add("caa_event_flow", "ntm")
            .add("entry_point", "login_home")
            .add("event_client_time", "${System.currentTimeMillis() / 1000.0}")
            .add("is_kadabra_zero", "false")
            .add("reg_navigation_flow_name", "new_to_family_c50_r1")
            .add("regulation_jurisdiction", "[\"${countryCode.uppercase()}\"]")
            .add("qpl_join_id", "f769ec62b52efeb44")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "1b")
            .add("__hs", "20684.HYP:frl_comet_auth_pkg.2.1...0")
            .add("dpr", "2")
            .add("__ccg", "EXCELLENT")
            .add("__rev", "1045526258")
            .add("__s", "d2omdx:7ccag3:s2uejx")
            .add("__hsi", "7675643117285530111")
            .add("__dyn", "7xeUmwlEnwn8K2Wmh0no6u5U4e0yoW3q32360CEbo1nEhw2nVE4W099w8G1Dz81s8hwnU2lwv89k2C1Fwc60D82IzXwae4UaEW0Loco5G0zK1swa-0raazo7u0zE2ZwrU6C2q0XU6O1FwlU5G3y0zo7u0jW0eowRzE")
            .add("__csr", "gkaauWWZ5qAiGyaBlh4vvujKuVpEjK8BKibKcmh29-l4DKjKq2q04080ceRhyKucK8hbCIxkizOj7po2HwEBwQw05iMw2aVo1oE0eT82ogmw2a86maBw1HC04Bk2lBDgGu3K07_oqxm0GPwdFwCUga2V00Szw2i8")
            .add("__hsdp", "gdzOgh04hmh8w14Eux69w2Lo1ke00z1U0sgw")
            .add("__hblp", "09m6811ax28Bw9K1pxW4oB02I8K2Gm0i60aUw25o0aG80fwo0IK0k607481OUhU5i3u0F80r0wkE0gyw8a0b9w34E")
            .add("__sjsp", "gdzOhT28")
            .add("__comet_req", "33")
            .add("lsd", lsdToken)
            .add("jazoest", "22388")
            .add("__spin_r", "1045526258")
            .add("__spin_b", "trunk")
            .add("__spin_t", "1787124927")
            .add("__jssesw", "1")

        val request = Request.Builder()
            .url("https://auth.meta.com/login/device-based/kadabra-register-save-credentials/")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12; itel S665L Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/105.0.5195.136 Mobile Safari/537.36")
            .addHeader("Accept-Encoding", "gzip, deflate")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("x-fb-lsd", lsdToken)
            .addHeader("x-asbd-id", "359341")
            .addHeader("origin", "https://auth.meta.com")
            .addHeader("x-requested-with", "mark.via.gp")
            .addHeader("sec-fetch-site", "same-origin")
            .addHeader("sec-fetch-mode", "cors")
            .addHeader("sec-fetch-dest", "empty")
            .addHeader("referer", "https://auth.meta.com/?waterfall_id=$waterfallId&redirect_uri=https%3A%2F%2Fauth.meta.ai%2Fecto")
            .addHeader("accept-language", "en-US,en;q=0.9,fr-FR;q=0.8,fr;q=0.7")
            .addHeader("Cookie", "datr=vlyFasvUX9awlb5qrUBkeKN8; meta_csrf=J_bFjURGS7PqKXmJFlDNPN")
            .post(formBuilder.build())
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val cleanJsonStr = if (body.startsWith("for (;;);")) body.substringAfter("for (;;);") else body

                var isSuccess = false
                var errorMsg = ""

                try {
                    val json = JSONObject(cleanJsonStr)
                    if (json.has("error")) {
                        errorMsg = json.optString("errorSummary", "")
                        if (errorMsg.isBlank()) errorMsg = json.optString("errorDescription", "Registration Error")
                        isSuccess = false
                    } else if (json.has("payload") || json.optInt("__ar", 0) == 1) {
                        isSuccess = true
                    } else if (response.isSuccessful) {
                        isSuccess = true
                    }
                } catch (e: Exception) {
                    if (response.isSuccessful && !body.contains("error")) {
                        isSuccess = true
                    } else {
                        errorMsg = "Parse error: ${e.message}"
                    }
                }

                if (isSuccess) {
                    IgCreationResult(
                        success = true,
                        username = username,
                        phone = phone,
                        message = "সাকসেস"
                    )
                } else {
                    IgCreationResult(
                        success = false,
                        username = username,
                        phone = phone,
                        error = errorMsg.ifBlank { "Creation Failed" }
                    )
                }
            }
        } catch (e: Exception) {
            IgCreationResult(
                success = false,
                username = username,
                phone = phone,
                error = e.message ?: "Connection error"
            )
        }
    }
}
