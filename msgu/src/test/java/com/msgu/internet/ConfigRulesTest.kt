package com.msgu.internet

import org.junit.Assert.*
import org.junit.Test

class ConfigRulesTest {
 @Test fun setupValidation() {
  assertTrue(ConfigRules.validUrl("https://example.supabase.co"))
  assertFalse(ConfigRules.validUrl("http://example.supabase.co"))
  assertFalse(ConfigRules.validUrl("https://example.com"))
  assertTrue(ConfigRules.validKey("sb_publishable_abcdef123456789012345"))
  assertFalse(ConfigRules.validKey("sb_secret_1234567890123456789012345"))
  assertFalse(ConfigRules.validKey("service_role"))
  assertTrue(ConfigRules.validUsername("hello_123"))
  assertFalse(ConfigRules.validUsername("A B"))
 }
}
