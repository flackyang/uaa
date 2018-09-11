/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.codec.binary.Base64;
import org.cloudfoundry.identity.uaa.oauth.token.TokenConstants;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.util.SetServerNameRequestPostProcessor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

import static org.cloudfoundry.identity.uaa.oauth.token.TokenConstants.TokenFormat.OPAQUE;
import static org.junit.Assume.assumeTrue;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.templates.TemplateFormats.markdown;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ActiveProfiles("default")
@ContextConfiguration(classes = TestSpringContext.class)
public class InjectedMockContextTest {

    protected RandomValueStringGenerator generator = new RandomValueStringGenerator(12) {
        @Override
        public String generate() {
            return super.generate().toLowerCase();
        }
    };

    @Rule
    public JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation("build/generated-snippets");

    protected static RandomValueStringGenerator gen = new RandomValueStringGenerator(8);

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    protected final TestClient testClient = new TestClient();
    private static volatile boolean mustDestroy = false;

    public GenericWebApplicationContext getWebApplicationContext() throws Exception {
        return (GenericWebApplicationContext) webApplicationContext;
    }

    public MockMvc getMockMvc() {
        return mockMvc;
    }

    public static boolean isMustDestroy() {
        return mustDestroy;
    }


    @Before
    public void initMockMvc() throws Exception {
        FilterChainProxy springSecurityFilterChain = getWebApplicationContext().getBean("springSecurityFilterChain", FilterChainProxy.class);
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(getWebApplicationContext())
                    .addFilter(springSecurityFilterChain)
                    .apply(documentationConfiguration(this.restDocumentation)
                            .uris().withPort(80).and()
                            .snippets()
                            .withTemplateFormat(markdown()))
                    .build();
        }
    }

    public TestClient getTestClient() {
        return testClient;
    }

    public static class SkipWhenNotRunningInSuiteRule implements TestRule {
        @Override
        public Statement apply(Statement statement, Description description) {
            assumeTrue(UaaBaseSuite.shouldMockTestBeRun());
            return statement;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OAuthToken {
        @JsonProperty("access_token")
        public String accessToken;

        public OAuthToken() {
        }
    }

    public class TestClient {
        public TestClient() {
        }

        public String getClientCredentialsOAuthAccessToken(String username, String password, String scope) throws Exception {
            return getClientCredentialsOAuthAccessToken(username, password, scope, null);
        }

        public String getClientCredentialsOAuthAccessToken(String username, String password, String scope, String subdomain)
                throws Exception {
            String basicDigestHeaderValue = "Basic "
                    + new String(Base64.encodeBase64((username + ":" + password).getBytes()));
            MockHttpServletRequestBuilder oauthTokenPost = post("/oauth/token")
                    .header("Authorization", basicDigestHeaderValue)
                    .param("grant_type", "client_credentials")
                    .param("client_id", username)
                    .param(TokenConstants.REQUEST_TOKEN_FORMAT, OPAQUE.getStringValue())
                    .param("scope", scope);
            if (subdomain != null && !subdomain.equals(""))
                oauthTokenPost.with(new SetServerNameRequestPostProcessor(subdomain + ".localhost"));
            MvcResult result = mockMvc.perform(oauthTokenPost)
                    .andExpect(status().isOk())
                    .andReturn();
            OAuthToken oauthToken = JsonUtils.readValue(result.getResponse().getContentAsString(), OAuthToken.class);
            return oauthToken.accessToken;
        }

        public String getUserOAuthAccessToken(String clientId, String clientSecret, String username, String password, String scope)
                throws Exception {
            String basicDigestHeaderValue = "Basic "
                    + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes()));
            MockHttpServletRequestBuilder oauthTokenPost = post("/oauth/token")
                    .header("Authorization", basicDigestHeaderValue)
                    .param("grant_type", "password")
                    .param("client_id", clientId)
                    .param("username", username)
                    .param("password", password)
                    .param(TokenConstants.REQUEST_TOKEN_FORMAT, OPAQUE.getStringValue())
                    .param("scope", scope);
            MvcResult result = mockMvc.perform(oauthTokenPost).andExpect(status().isOk()).andReturn();
            OAuthToken oauthToken = JsonUtils.readValue(result.getResponse().getContentAsString(), OAuthToken.class);
            return oauthToken.accessToken;
        }

        public String getUserOAuthAccessTokenForZone(String clientId, String clientSecret, String username, String password, String scope, String subdomain) throws Exception {
            String basicDigestHeaderValue = "Basic "
                    + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes()));
            MockHttpServletRequestBuilder oauthTokenPost = post("/oauth/token")
                    .header("Authorization", basicDigestHeaderValue)
                    .param("grant_type", "password")
                    .param("client_id", clientId)
                    .param("username", username)
                    .param("password", password)
                    .param(TokenConstants.REQUEST_TOKEN_FORMAT, TokenConstants.TokenFormat.JWT.getStringValue())
                    .param("scope", scope);
            oauthTokenPost.header("Host", subdomain + ".localhost");
            MvcResult result = mockMvc.perform(oauthTokenPost).andExpect(status().isOk()).andReturn();
            OAuthToken oauthToken = JsonUtils.readValue(result.getResponse().getContentAsString(), OAuthToken.class);
            return oauthToken.accessToken;
        }
    }
}
