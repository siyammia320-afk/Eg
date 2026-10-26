package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `verify ApiSecurityManager 3x Base64 keys and integrity`() {
    val isValid = com.example.security.ApiSecurityManager.isIntegrityValid()
    assertTrue("Integrity check must pass for embedded Base64 keys", isValid)

    val key = com.example.security.ApiSecurityManager.getAuthorizedApiKey()
    assertEquals("Decoded authorized key must match MJMUGO5RU9O", "MJMUGO5RU9O", key)
  }
}
