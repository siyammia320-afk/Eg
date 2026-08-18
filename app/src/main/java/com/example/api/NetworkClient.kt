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
        try {
            val cleanRid = rangeCode.replace("X", "").replace("x", "").trim().ifEmpty { "8801" }
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

    suspend fun createFacebookAccount(
        rawPhone: String,
        password: String,
        profile: com.example.data.GeneratedAccountProfile? = null
    ): FbCreationResult = withContext(Dispatchers.IO) {
        val phone = rawPhone.replace(Regex("[^0-9]"), "")
        val firstName = profile?.firstName ?: FRENCH_FIRST_NAMES.random()
        val lastName = profile?.lastName ?: FRENCH_LAST_NAMES.random()
        val fullName = profile?.fullName ?: "$firstName $lastName"
        val day = profile?.day ?: (1..28).random().toString()
        val month = profile?.month ?: (1..12).random().toString()
        val year = profile?.year ?: (1980..2005).random().toString()
        val sexCode = profile?.sexCode ?: "2"

        val androidUa = "Mozilla/5.0 (Linux; Android 12; itel S665L Build/SP1A.210812.016) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.7827.91 Mobile Safari/537.36"

        val formBody = FormBody.Builder()
            .add("ccp", "2")
            .add("submission_request", "true")
            .add("helper", "")
            .add("reg_impression_id", UUID.randomUUID().toString())
            .add("ns", "1")
            .add("zero_header_af_client", "")
            .add("app_id", "103")
            .add("logger_id", UUID.randomUUID().toString())
            .add("field_names[0]", "firstname")
            .add("firstname", firstName)
            .add("lastname", lastName)
            .add("field_names[1]", "birthday_wrapper")
            .add("birthday_day", day)
            .add("birthday_month", month)
            .add("birthday_year", year)
            .add("age_step_input", "")
            .add("did_use_age", "false")
            .add("field_names[2]", "reg_email__")
            .add("reg_email__", phone)
            .add("field_names[3]", "sex")
            .add("sex", sexCode)
            .add("preferred_pronoun", "")
            .add("custom_gender", "")
            .add("reg_passwd__", password)
            .add("name_suggest_elig", "false")
            .add("was_shown_name_suggestions", "false")
            .add("did_use_suggested_name", "false")
            .add("use_custom_gender", "false")
            .add("guid", "")
            .add("pre_form_step", "")
            .add("submit", "Sign up")
            .add("fb_dtsg", "NAfx5UxG44eai86HC1iwiixBs1mUDFhn3ccN1fj3-SJJc64TeUsEAEg:0:0")
            .add("jazoest", "24748")
            .add("lsd", "AdRCh7SdER7Za5PotUuics5fFt0")
            .add("__dyn", "1Z3pawlEnwm8_Bg9ppoW5UdE4a2i5U4e0C86u7E39x60zU3ex608ewk9E4W0pKq0FE6S0x81vohw73wGwcq1GwqU2YwbK0oi0zE1jU1soG0hi0Lo6-0Co1kU1UU3jwea")
            .add("__csr", "")
            .add("__hsdp", "")
            .add("__hblp", "")
            .add("__sjsp", "")
            .add("__req", "g")
            .add("__fmt", "1")
            .add("__a", "AYzJ_41FhHOHmeaJtz_y-NZ41BrpCkk8MZbenM7ATpRLY9c4d3QLNQW9sph6SN5jNJBH5tH1yvE_P-EybRqM6tZ_nqLEaV4b3ZU")
            .add("__user", "0")
            .build()

        val fbUrl = "https://limited.facebook.com/reg/submit/?privacy_mutation_token=eyJ0eXBlIjowLCJjcmVhdGlvbl90aW1lIjoxNzgyMTQ5MzY4LCJjYWxsc2l0ZV9pZCI6OTA3OTI0NDAyOTQ4MDU4fQ%3D%3D&app_id=103&multi_step_form=1&skip_suma=0&shouldForceMTouch=1"

        val request = Request.Builder()
            .url(fbUrl)
            .addHeader("User-Agent", androidUa)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
            .addHeader("Connection", "keep-alive")
            .addHeader("Upgrade-Insecure-Requests", "1")
            .addHeader("sec-ch-ua-platform", "\"Android\"")
            .addHeader("sec-ch-ua", "\"Android WebView\";v=\"149\", \"Chromium\";v=\"149\", \"Not)A;Brand\";v=\"24\"")
            .addHeader("sec-ch-ua-mobile", "?1")
            .addHeader("x-response-format", "JSONStream")
            .addHeader("x-asbd-id", "359341")
            .addHeader("x-fb-lsd", "AdRCh7SdER7Za5PotUuics5fFt0")
            .addHeader("x-requested-with", "XMLHttpRequest")
            .addHeader("origin", "https://limited.facebook.com")
            .addHeader("sec-fetch-site", "same-origin")
            .addHeader("sec-fetch-mode", "cors")
            .addHeader("sec-fetch-dest", "empty")
            .addHeader("referer", "https://limited.facebook.com/reg/?is_two_steps_login=0&cid=103&refsrc=deprecated&soft=hjk")
            .addHeader("priority", "u=1, i")
            .post(formBody)
            .build()

        try {
            val startTime = System.currentTimeMillis()
            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val headers = response.headers
                val setCookies = headers.values("Set-Cookie")
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

                // Check for c_user in body text via various patterns if not in Set-Cookie headers
                if (extractedUid.isEmpty()) {
                    val uidPatterns = listOf(
                        Pattern.compile("c_user=(\\d+)"),
                        Pattern.compile("\"c_user\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"USER_ID\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"actorID\"\\s*:\\s*\"?(\\d+)\"?"),
                        Pattern.compile("\"account_id\"\\s*:\\s*\"?(\\d+)\"?")
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

                if (extractedUid.isNotEmpty()) {
                    val baseCookies = if (fullCookieStr.isNotEmpty()) fullCookieStr else ""
                    val finalCookie = if (baseCookies.contains("c_user=")) {
                        baseCookies
                    } else if (baseCookies.isNotEmpty()) {
                        "c_user=$extractedUid; $baseCookies"
                    } else {
                        "c_user=$extractedUid; phone=$phone; pass=$password"
                    }

                    return@withContext FbCreationResult(
                        success = true,
                        phone = phone,
                        uid = extractedUid,
                        name = fullName,
                        password = password,
                        cookie = finalCookie
                    )
                } else {
                    return@withContext FbCreationResult(
                        success = false,
                        phone = phone,
                        error = "Facebook creation failed: No c_user or UID returned"
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

    suspend fun sendTelegramOtp(chatId: String, number: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        if (chatId.isBlank()) return@withContext false
        val token = "8870596268:AAGGk8fG8w0OA3J8-MJOqctToaLkrh2zFMU"
        val telegramUrl = "https://api.telegram.org/bot$token/sendMessage"
        
        val messageText = "`$number`\n`$otp`"

        try {
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId.trim())
                put("text", messageText)
                put("parse_mode", "Markdown")
            }

            val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(telegramUrl)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
