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

data class ConsoleHit(
    val range: String,
    val sid: String,
    val message: String,
    val time: Long
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
    val DEFAULT_API_KEY: String
        get() = com.example.security.ApiSecurityManager.getAuthorizedApiKey()
    var currentApiKey: String
        get() = com.example.security.ApiSecurityManager.getAuthorizedApiKey()
        set(value) {
            com.example.security.ApiSecurityManager.validateExternalKeyOrExit(value, null)
        }

    const val FIXED_PASSWORD = "arafat@@##"

    // V1 Endpoints & Fallback Tokens (limited.facebook.com)
    private const val V1_GET_URL = "https://limited.facebook.com/reg/?cid=103&refsrc=deprecated&soft=hjk"
    private const val V1_SUBMIT_URL = "https://limited.facebook.com/reg/submit/?app_id=103&multi_step_form=1&skip_suma=0&shouldForceMTouch=1"

    private const val V1_FALLBACK_LSD = "AdTVmPkwJP3Cq-5SK-ewKKD5Wp8"
    private const val V1_FALLBACK_FB_DTSG = "NAfx4_3yUPPiwdOADtHmT6aHnwopW_MmPDgIjjkK-1qlKZD8UDCX95A:0:0"
    private const val V1_FALLBACK_JAZOEST = "24906"
    private const val V1_FALLBACK_DYN = "0wzpawlEnwm8_Bg9ppoW5UdE4a2i5U4e0C86u7E39x60zU3ex608ewk9E4W0pKq0FE6S0x81vohw73wGwcq1GwqU2YwbK0oi0zE1jU1soG0hi0Lo6-0Co1kU1UU3jwea"
    private const val V1_FALLBACK_A = "AY0mgpOgOqHZOaxMdEKO8t_1AaG3xe5WmHtaDgVUMVToIcNY0QqUS4jTS6PNePF_x9AHTzBhQV1ZwCN1gq4UBHfJlW1oKItbwlw"

    // GraphQL Checker Constants
    private const val CHECKER_GRAPHQL_URL = "https://www.facebook.com/api/graphql/"
    private const val CHECKER_DOC_ID = "27012969198380071"
    private const val CHECKER_LSD = "AdS-QY2mYrCcHP7sVf7ynTA6vTc"
    private const val CHECKER_JAZOEST = "22291"
    private const val CHECKER_CIPHER_TEXT = "AdAtB3O832KrYgWu2c7Jm2mNWOdAALA9wNFKJ_043RSsW27RSXXjhlBd0Cr8rGn-lIwgDuQU-CNQILiqKbqFW0GZop7nLgDVhClntKlePmNpHzT4ESf47v-lTUCNV3L-ikw70LhayHi30trSUJqk-Rvmaost-m0CTl60dFiIcbLNhGmQ8uggs-M1wfu04KQJvOQBDLwohSr7f_yRqkDGj98Ra-1S2N77k17Fqxu8N84cEWWTyv4qW6ySj7GI7CYYhD8QZDTzMxXcImxAr_Y"

    private val usedNumbersCache = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /**
     * GET /console
     * Global live feed of recent hits.
     */
    suspend fun fetchConsoleHits(): List<ConsoleHit> = withContext(Dispatchers.IO) {
        val hitsList = mutableListOf<ConsoleHit>()
        try {
            val request = Request.Builder()
                .url("$API_BASE_URL/console")
                .addHeader("mauthapi", currentApiKey)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext hitsList
                val bodyStr = response.body?.string() ?: return@withContext hitsList
                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                if (meta?.optInt("code") == 200) {
                    val data = json.optJSONObject("data")
                    val hitsArray = data?.optJSONArray("hits")
                    if (hitsArray != null) {
                        for (i in 0 until hitsArray.length()) {
                            val obj = hitsArray.getJSONObject(i)
                            val range = obj.optString("range", "").trim()
                            val sid = obj.optString("sid", "").trim()
                            val message = obj.optString("message", "").trim()
                            val time = obj.optLong("time", 0L)
                            if (range.isNotBlank()) {
                                hitsList.add(ConsoleHit(range, sid, message, time))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext hitsList
    }

    /**
     * GET /liveaccess
     * Fetches recently-active services + ranges, enriched with live console messages.
     */
    suspend fun getLiveFacebookRanges(): List<RangeItem> = withContext(Dispatchers.IO) {
        val rangesList = mutableListOf<RangeItem>()
        val seenCodes = mutableSetOf<String>()
        val rangeToMessage = mutableMapOf<String, String>()

        // 1. Fetch live console hits for latest message under each range
        val consoleHits = fetchConsoleHits()
        for (hit in consoleHits) {
            val cleanCode = hit.range.trim()
            if (cleanCode.isNotBlank()) {
                if (hit.message.isNotBlank() && !rangeToMessage.containsKey(cleanCode)) {
                    rangeToMessage[cleanCode] = hit.message
                }
                val isFbHit = hit.sid.contains("Face", ignoreCase = true) || hit.sid.contains("Fb", ignoreCase = true)
                if (isFbHit && seenCodes.add(cleanCode)) {
                    rangesList.add(RangeItem(cleanCode, hit.message))
                }
            }
        }

        // 2. Fetch /liveaccess
        try {
            val request = Request.Builder()
                .url("$API_BASE_URL/liveaccess")
                .addHeader("mauthapi", currentApiKey)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotBlank()) {
                        val json = JSONObject(bodyStr)
                        val meta = json.optJSONObject("meta")
                        if (meta?.optInt("code") == 200) {
                            val data = json.optJSONObject("data")
                            val services = data?.optJSONArray("services")
                            if (services != null) {
                                for (i in 0 until services.length()) {
                                    val serviceObj = services.getJSONObject(i)
                                    val sid = serviceObj.optString("sid", "")
                                    if (sid.contains("Face", ignoreCase = true) || sid.contains("Fb", ignoreCase = true) || sid.contains("Meta", ignoreCase = true)) {
                                        val rangesArray = serviceObj.optJSONArray("ranges")
                                        if (rangesArray != null) {
                                            for (j in 0 until rangesArray.length()) {
                                                val elem = rangesArray.get(j)
                                                val code = if (elem is JSONObject) {
                                                    elem.optString("range", "").ifEmpty { elem.optString("code", "") }
                                                } else {
                                                    elem.toString()
                                                }.trim()

                                                if (code.isNotBlank() && seenCodes.add(code)) {
                                                    val msg = rangeToMessage[code] ?: ""
                                                    rangesList.add(RangeItem(code, msg))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback active ranges if panel is loading or empty
        if (rangesList.isEmpty()) {
            val defaults = listOf("22501XXX", "8801XXX", "22507XXX", "23490XXX", "23481XXX")
            for (def in defaults) {
                val msg = rangeToMessage[def] ?: ""
                rangesList.add(RangeItem(def, msg))
            }
        } else {
            for (i in rangesList.indices) {
                val item = rangesList[i]
                if (item.message.isBlank() && rangeToMessage.containsKey(item.code)) {
                    rangesList[i] = item.copy(message = rangeToMessage[item.code] ?: "")
                }
            }
        }

        return@withContext rangesList
    }

    /**
     * POST /getnum
     * Allocates one number from range without 'XXX'.
     */
    suspend fun fetchNumber(rangeCode: String): String? = withContext(Dispatchers.IO) {
        if (rangeCode.isBlank()) return@withContext null
        try {
            val cleanRid = rangeCode.replace("X", "", ignoreCase = true).trim()
            if (cleanRid.isEmpty()) return@withContext null
            val jsonBody = JSONObject().put("rid", cleanRid).toString()

            val request = Request.Builder()
                .url("$API_BASE_URL/getnum")
                .addHeader("mauthapi", currentApiKey)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                if (meta?.optInt("code") == 200) {
                    val data = json.optJSONObject("data")
                    val fullNum = data?.optString("full_number")
                        ?: data?.optString("no_plus_number")
                        ?: data?.optString("national_number")
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

    /**
     * GraphQL Phone Availability Checker
     * Returns Pair(status, description) where status is "free", "taken", or "error".
     */
    suspend fun checkNumberGraphql(phoneNumber: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val cleanPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+$phoneNumber"
        try {
            val variables = JSONObject().apply {
                put("params", JSONObject().apply {
                    put("cipher_text", CHECKER_CIPHER_TEXT)
                    put("context", "recover")
                    put("event_request_id", UUID.randomUUID().toString())
                    put("friend_name", "")
                    put("search_query", cleanPhone)
                    put("waterfall_id", UUID.randomUUID().toString())
                })
            }

            val formBody = FormBody.Builder()
                .add("av", "0")
                .add("__aaid", "0")
                .add("__user", "0")
                .add("__a", "1")
                .add("__req", "5")
                .add("__hs", "20742.HYP:comet_loggedout_pkg.2.1...0")
                .add("dpr", "2")
                .add("__ccg", "EXCELLENT")
                .add("__rev", "1050579926")
                .add("__comet_req", "15")
                .add("lsd", CHECKER_LSD)
                .add("jazoest", CHECKER_JAZOEST)
                .add("__spin_r", "1050579926")
                .add("__spin_b", "trunk")
                .add("__spin_t", (System.currentTimeMillis() / 1000).toString())
                .add("__crn", "comet.fbweb.CometCAAAccountSearchRoute")
                .add("qpl_active_flow_ids", "516759801")
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "CAAFBAccountSearchViewQuery")
                .add("server_timestamps", "true")
                .add("variables", variables.toString())
                .add("doc_id", CHECKER_DOC_ID)
                .add("fb_api_analytics_tags", "[\"qpl_active_flow_ids=516759801\"]")
                .build()

            val request = Request.Builder()
                .url(CHECKER_GRAPHQL_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("x-fb-friendly-name", "CAAFBAccountSearchViewQuery")
                .addHeader("x-fb-lsd", CHECKER_LSD)
                .addHeader("origin", "https://www.facebook.com")
                .addHeader("referer", "https://www.facebook.com/login/identify/")
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Pair("error", "HTTP ${response.code}")
                }
                val bodyStr = response.body?.string() ?: ""
                val js = JSONObject(bodyStr)
                val data = js.optJSONObject("data")
                val search = data?.optJSONObject("caa_ar_fb_account_search")
                val accounts = search?.optJSONArray("accounts")
                val errorContent = search?.optJSONObject("error_content")
                val description = errorContent?.optString("description", "")?.trim() ?: ""

                if ((accounts == null || accounts.length() == 0) && description.contains("No account found", ignoreCase = true)) {
                    return@withContext Pair("free", description)
                }
                if (accounts != null && accounts.length() > 0) {
                    return@withContext Pair("taken", "${accounts.length()} account(s)")
                }
                if (description.isNotEmpty()) {
                    return@withContext Pair("taken", description)
                }
                return@withContext Pair("unknown", "No clear result")
            }
        } catch (e: Exception) {
            return@withContext Pair("error", e.message ?: "Checker error")
        }
    }

    /**
     * Acquire a checked number from range: ensures number is not already taken
     */
    suspend fun acquireCheckedNumber(rangeCode: String): Pair<String?, String> = withContext(Dispatchers.IO) {
        var fallback: Pair<String?, String>? = null

        for (attempt in 1..8) {
            val num = fetchNumber(rangeCode)
            if (num.isNullOrBlank() || usedNumbersCache.contains(num)) {
                kotlinx.coroutines.delay(500)
                continue
            }

            val (status, desc) = checkNumberGraphql(num)
            if (status == "free") {
                usedNumbersCache.add(num)
                return@withContext Pair(num, "FREE")
            }

            if (status == "taken") {
                usedNumbersCache.add(num)
                kotlinx.coroutines.delay(400)
                continue
            }

            if (fallback == null) {
                fallback = Pair(num, status.uppercase())
            }
        }

        if (fallback != null) {
            fallback.first?.let { usedNumbersCache.add(it) }
            return@withContext fallback
        }

        return@withContext Pair(null, "NO_FREE_NUMBERS")
    }

    private fun generateDynamicUa(): String {
        val androidVer = (11..14).random()
        val chromeVer = (150..160).random()
        val buildNum = (8000..8200).random()
        val patch = (30..80).random()
        return "Mozilla/5.0 (Linux; Android $androidVer; itel S665L Build/SP1A.210813.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVer.0.$buildNum.$patch Mobile Safari/537.36"
    }

    private fun generateDatr(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
        return (1..24).map { chars.random() }.joinToString("")
    }

    /**
     * V1 Account Creation (limited.facebook.com)
     */
    suspend fun createFacebookAccount(
        rawPhone: String,
        password: String = FIXED_PASSWORD,
        profile: com.example.data.GeneratedAccountProfile? = null
    ): FbCreationResult = withContext(Dispatchers.IO) {
        val phone = rawPhone.replace(Regex("[^0-9]"), "")
        val firstName = profile?.firstName ?: "Ayesha"
        val lastName = profile?.lastName ?: "Akter"
        val fullName = profile?.fullName ?: "$firstName $lastName"
        val day = profile?.day ?: (15..28).random().toString()
        val month = profile?.month ?: (1..12).random().toString()
        val year = profile?.year ?: (1992..2002).random().toString()
        val sexCode = profile?.sexCode ?: "2"

        val dynamicUa = generateDynamicUa()
        val datrCookie = generateDatr()

        var extractedLsd = V1_FALLBACK_LSD
        var extractedFbDtsg = V1_FALLBACK_FB_DTSG
        var extractedJazoest = V1_FALLBACK_JAZOEST
        var extractedDyn = V1_FALLBACK_DYN
        var extractedA = V1_FALLBACK_A
        var regInstance = datrCookie

        // 1. Initial GET to fetch dynamic tokens & session cookies
        try {
            val getRequest = Request.Builder()
                .url(V1_GET_URL)
                .addHeader("User-Agent", dynamicUa)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Cookie", "datr=$datrCookie")
                .get()
                .build()

            okHttpClient.newCall(getRequest).execute().use { getRes ->
                val html = getRes.body?.string() ?: ""
                val lsdMatch = Regex("""name="lsd"[^>]*value="([^"]+)"""").find(html)
                    ?: Regex(""""token":"([^"]+)"""").find(html)
                if (lsdMatch != null) extractedLsd = lsdMatch.groupValues[1]

                val dtsgMatch = Regex("""name="fb_dtsg"[^>]*value="([^"]+)"""").find(html)
                if (dtsgMatch != null) extractedFbDtsg = dtsgMatch.groupValues[1]

                val regInstMatch = Regex("""name="reg_instance"[^>]*value="([^"]+)"""").find(html)
                if (regInstMatch != null) regInstance = regInstMatch.groupValues[1]
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Submit V1 registration form
        val formBody = FormBody.Builder()
            .add("ccp", "2")
            .add("reg_instance", regInstance)
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
            .add("field_names[4]", "reg_passwd__")
            .add("reg_passwd__", password)
            .add("was_shown_name_suggestions", "false")
            .add("did_use_suggested_name", "false")
            .add("use_custom_gender", "false")
            .add("guid", "")
            .add("pre_form_step", "")
            .add("submit", "Sign up")
            .add("fb_dtsg", extractedFbDtsg)
            .add("jazoest", extractedJazoest)
            .add("lsd", extractedLsd)
            .add("__dyn", extractedDyn)
            .add("__csr", "")
            .add("__hsdp", "")
            .add("__hblp", "")
            .add("__sjsp", "")
            .add("__req", "g")
            .add("__fmt", "1")
            .add("__a", extractedA)
            .add("__user", "0")
            .build()

        val postRequest = Request.Builder()
            .url(V1_SUBMIT_URL)
            .addHeader("User-Agent", dynamicUa)
            .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("x-response-format", "JSONStream")
            .addHeader("x-asbd-id", "359341")
            .addHeader("x-fb-lsd", extractedLsd)
            .addHeader("origin", "https://limited.facebook.com")
            .addHeader("referer", V1_GET_URL)
            .addHeader("Cookie", "datr=$datrCookie")
            .post(formBody)
            .build()

        try {
            okHttpClient.newCall(postRequest).execute().use { response ->
                val setCookies = response.headers.values("Set-Cookie")
                var extractedUid = ""
                val cookieBuilder = StringBuilder()
                cookieBuilder.append("datr=$datrCookie; ")

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
                        password = password,
                        cookie = finalCookie
                    )
                } else if (response.isSuccessful || bodyStr.contains("redirect")) {
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
                        password = password,
                        cookie = finalCookie
                    )
                } else {
                    return@withContext FbCreationResult(
                        success = false,
                        phone = phone,
                        error = "Creation response: No user token returned"
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

    /**
     * GET /success-otp
     * Returns last 50 successful OTPs.
     */
    suspend fun checkOtps(): List<OtpItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<OtpItem>()
        try {
            val request = Request.Builder()
                .url("$API_BASE_URL/success-otp")
                .addHeader("mauthapi", currentApiKey)
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
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (otpsArray != null) {
                    for (i in 0 until otpsArray.length()) {
                        val item = otpsArray.optJSONObject(i) ?: continue
                        val num = item.optString("number", "")
                            .ifEmpty { item.optString("phone", "") }
                            .ifEmpty { item.optString("full_number", "") }
                            .replace("+", "").trim()

                        var directOtp = item.optString("otp", "")
                            .ifEmpty { item.optString("code", "") }
                            .trim()

                        val msg = item.optString("message", "")
                            .ifEmpty { item.optString("sms", "") }

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

    fun extractOtpFromText(text: String): String {
        if (text.isBlank()) return "N/A"
        val m = Pattern.compile("\\b(\\d{4,8})\\b").matcher(text)
        if (m.find()) {
            return m.group(1) ?: "N/A"
        }
        return "N/A"
    }

    fun formatCleanCookie(
        rawCookie: String,
        uid: String = ""
    ): String {
        val cleanPairs = LinkedHashMap<String, String>()
        cleanPairs["datr"] = "z8V_ajxf-8PdZE6c8huwEzqD"
        cleanPairs["sb"] = "0MV_ar8A9ecW5cQXAbm4EX9D"
        cleanPairs["fr"] = "0vyhAt6gpZrRsGnhb..Bqf8XQ..AAA.0.0.Bqf8XQ.AWfMsvOpiMmcD2458vHBO-uB2k0"

        if (rawCookie.isNotBlank()) {
            val tokens = rawCookie.split(";").flatMap { it.split(",") }
            for (token in tokens) {
                val trimmed = token.trim()
                if (trimmed.contains("=")) {
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim()
                    val lowerKey = key.lowercase()

                    if (lowerKey !in setOf(
                            "path", "domain", "expires", "max-age", "secure", "httponly",
                            "samesite", "version", "phone", "pass", "password", "number"
                        )) {
                        if (key.isNotBlank() && value.isNotBlank()) {
                            cleanPairs[key] = value
                        }
                    }
                }
            }
        }

        if (uid.isNotBlank()) {
            cleanPairs["c_user"] = uid
        }

        val joined = cleanPairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return if (joined.endsWith(";")) joined else "$joined;"
    }
}
