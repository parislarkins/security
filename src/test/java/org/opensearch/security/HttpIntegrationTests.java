/*
 * Copyright 2015-2018 _floragunn_ GmbH
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest.RefreshPolicy;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.security.action.configupdate.ConfigUpdateAction;
import org.opensearch.security.action.configupdate.ConfigUpdateRequest;
import org.opensearch.security.action.configupdate.ConfigUpdateResponse;
import org.opensearch.security.ssl.util.SSLConfigConstants;
import org.opensearch.security.support.ConfigConstants;
import org.opensearch.security.test.DynamicSecurityConfig;
import org.opensearch.security.test.SingleClusterTest;
import org.opensearch.security.test.helper.file.FileHelper;
import org.opensearch.security.test.helper.rest.RestHelper;
import org.opensearch.security.test.helper.rest.RestHelper.HttpResponse;
import org.opensearch.transport.client.Client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.opensearch.security.DefaultObjectMapper.readTree;

public class HttpIntegrationTests extends SingleClusterTest {

    @Test
    public void testHTTPBasic() throws Exception {
        final Settings settings = Settings.builder()
            .putList(ConfigConstants.SECURITY_AUTHCZ_REST_IMPERSONATION_USERS + ".worf", "knuddel", "nonexists")
            .build();
        setup(settings);
        final RestHelper rh = nonSslRestHelper();

        try (Client tc = getClient()) {
            tc.admin().indices().create(new CreateIndexRequest("copysf")).actionGet();
            tc.index(new IndexRequest("vulcangov").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("starfleet").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(
                new IndexRequest("starfleet_academy").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest("starfleet_library").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest("klingonempire").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();
            tc.index(new IndexRequest("public").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("v2").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("v3").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            tc.index(new IndexRequest("spock").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("kirk").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(
                new IndexRequest("role01_role02").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();

            tc.admin()
                .indices()
                .aliases(
                    new IndicesAliasesRequest().addAliasAction(
                        AliasActions.add().indices("starfleet", "starfleet_academy", "starfleet_library").alias("sf")
                    )
                )
                .actionGet();
            tc.admin()
                .indices()
                .aliases(
                    new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("klingonempire", "vulcangov").alias("nonsf"))
                )
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("public").alias("unrestricted")))
                .actionGet();

        }

        assertThat(rh.executeGetRequest("").getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("_search").getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("nagilum", "nagilum")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeDeleteRequest("nonexistentindex*", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeGetRequest(".nonexistentindex*", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest(".opendistro_security/_doc/2", "{}", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_NOT_FOUND,
            is(rh.executeGetRequest(".opendistro_security/_doc/0", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_NOT_FOUND,
            is(rh.executeGetRequest("xxxxyyyy/_doc/0", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(rh.executeGetRequest("", encodeBasicHeader("abc", "abc:abc")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", encodeBasicHeader("userwithnopassword", "")).getStatusCode()));
        assertThat(
            HttpStatus.SC_UNAUTHORIZED,
            is(rh.executeGetRequest("", encodeBasicHeader("userwithblankpassword", "")).getStatusCode())
        );
        assertThat(rh.executeGetRequest("", encodeBasicHeader("worf", "wrongpasswd")).getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(
            HttpStatus.SC_UNAUTHORIZED,
            is(rh.executeGetRequest("", new BasicHeader("Authorization", "Basic " + "wrongheader")).getStatusCode())
        );
        assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", new BasicHeader("Authorization", "Basic ")).getStatusCode()));
        assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", new BasicHeader("Authorization", "Basic")).getStatusCode()));
        assertThat(rh.executeGetRequest("", new BasicHeader("Authorization", "")).getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("picard", "picard")).getStatusCode(), is(HttpStatus.SC_OK));

        for (int i = 0; i < 10; i++) {
            assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", encodeBasicHeader("worf", "wrongpasswd")).getStatusCode()));
        }

        assertThat(
            HttpStatus.SC_OK,
            is(rh.executePutRequest("/theindex", "{}", encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_CREATED,
            is(
                rh.executePutRequest("/theindex/_doc/1?refresh=true", "{\"a\":0}", encodeBasicHeader("theindexadmin", "theindexadmin"))
                    .getStatusCode()
            )
        );
        // assertThat(HttpStatus.SC_OK,
        // rh.executeGetRequest("/theindex/_analyze?text=this+is+a+test",encodeBasicHeader("theindexadmin",
        // "theindexadmin")).getStatusCode());
        // assertThat(HttpStatus.SC_FORBIDDEN,
        // rh.executeGetRequest("_analyze?text=this+is+a+test",encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode());
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeDeleteRequest("/theindex", encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeDeleteRequest("/klingonempire", encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode())
        );
        assertThat(rh.executeGetRequest("starfleet/_search", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(rh.executeGetRequest("_search", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_FORBIDDEN));
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeGetRequest("starfleet/_search?pretty", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeDeleteRequest(".opendistro_security/", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePostRequest("/.opendistro_security/_close", null, encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePostRequest("/.opendistro_security/_upgrade", null, encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest("/.opendistro_security/_mapping", "{}", encodeBasicHeader("worf", "worf")).getStatusCode())
        );

        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest(".opendistro_security/", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest(".opendistro_security/_doc/2", "{}", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest(".opendistro_security/_doc/0", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeDeleteRequest(".opendistro_security/_doc/0", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest(".opendistro_security/_doc/0", "{}", encodeBasicHeader("worf", "worf")).getStatusCode())
        );

        HttpResponse resc = rh.executeGetRequest("_cat/indices/public?v", encodeBasicHeader("bug108", "nagilum"));
        Assert.assertTrue(resc.getBody().contains("green"));
        assertThat(resc.getStatusCode(), is(HttpStatus.SC_OK));

        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "role01_role02/_search?pretty",
                    encodeBasicHeader("user_role01_role02_role03", "user_role01_role02_role03")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest("role01_role02/_search?pretty", encodeBasicHeader("user_role01", "user_role01")).getStatusCode())
        );

        assertThat(HttpStatus.SC_OK, is(rh.executeGetRequest("spock/_search?pretty", encodeBasicHeader("spock", "spock")).getStatusCode()));
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest("spock/_search?pretty", encodeBasicHeader("kirk", "kirk")).getStatusCode())
        );
        assertThat(HttpStatus.SC_OK, is(rh.executeGetRequest("kirk/_search?pretty", encodeBasicHeader("kirk", "kirk")).getStatusCode()));

        // all
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(
                rh.executePostRequest(".opendistro_security/_mget", "{\"ids\" : [\"0\"]}", encodeBasicHeader("worf", "worf"))
                    .getStatusCode()
            )
        );

        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeGetRequest("starfleet/_search?pretty", encodeBasicHeader("worf", "worf")).getStatusCode())
        );

        try (Client tc = getClient()) {
            tc.index(
                new IndexRequest(".opendistro_security").id("roles")
                    .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("roles", FileHelper.readYamlContent("roles_deny.yml"))
            ).actionGet();
            ConfigUpdateResponse cur = tc.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(new String[] { "roles" }))
                .actionGet();
            assertThat(cur.getNodes().size(), is(clusterInfo.numNodes));
        }

        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest("starfleet/_search?pretty", encodeBasicHeader("worf", "worf")).getStatusCode())
        );

        try (Client tc = getClient()) {
            tc.index(
                new IndexRequest(".opendistro_security").id("roles")
                    .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("roles", FileHelper.readYamlContent("roles.yml"))
            ).actionGet();
            ConfigUpdateResponse cur = tc.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(new String[] { "roles" }))
                .actionGet();
            assertThat(cur.getNodes().size(), is(clusterInfo.numNodes));
        }

        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeGetRequest("starfleet/_search?pretty", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        HttpResponse res = rh.executeGetRequest("_search?pretty", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("\"value\" : 11"));
        Assert.assertFalse(res.getBody().contains(".opendistro_security"));

        res = rh.executeGetRequest("_nodes/stats?pretty", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("total_in_bytes"));
        Assert.assertTrue(res.getBody().contains("max_file_descriptors"));
        Assert.assertTrue(res.getBody().contains("buffer_pools"));
        Assert.assertFalse(res.getBody().contains("\"nodes\" : { }"));

        res = rh.executePostRequest("*/_upgrade", "", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));

        String bulkBody = "{ \"index\" : { \"_index\" : \"test\", \"_id\" : \"1\" } }"
            + System.lineSeparator()
            + "{ \"field1\" : \"value1\" }"
            + System.lineSeparator()
            + "{ \"index\" : { \"_index\" : \"test\", \"_id\" : \"2\" } }"
            + System.lineSeparator()
            + "{ \"field2\" : \"value2\" }"
            + System.lineSeparator();

        res = rh.executePostRequest("_bulk", bulkBody, encodeBasicHeader("writer", "writer"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("\"errors\":false"));
        Assert.assertTrue(res.getBody().contains("\"status\":201"));

        res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("security_tenant", "unittesttenant"),
            encodeBasicHeader("worf", "worf")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("tenant"));
        Assert.assertTrue(res.getBody().contains("unittesttenant"));
        Assert.assertTrue(res.getBody().contains("\"kltentrw\":true"));
        Assert.assertTrue(res.getBody().contains("\"user_name\":\"worf\""));

        res = rh.executeGetRequest("_opendistro/_security/authinfo", encodeBasicHeader("worf", "worf"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("tenant"));
        Assert.assertTrue(res.getBody().contains("\"user_requested_tenant\":null"));
        Assert.assertTrue(res.getBody().contains("\"kltentrw\":true"));
        Assert.assertTrue(res.getBody().contains("\"user_name\":\"worf\""));
        Assert.assertTrue(res.getBody().contains("\"custom_attribute_names\":[]"));
        Assert.assertFalse(res.getBody().contains("attributes="));

        res = rh.executeGetRequest("_opendistro/_security/authinfo?pretty", encodeBasicHeader("custattr", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("tenants"));
        Assert.assertTrue(res.getBody().contains("\"user_requested_tenant\" : null"));
        Assert.assertTrue(res.getBody().contains("\"user_name\" : \"custattr\""));
        Assert.assertTrue(res.getBody().contains("\"custom_attribute_names\" : ["));
        Assert.assertTrue(res.getBody().contains("attr.internal.c3"));
        Assert.assertTrue(res.getBody().contains("attr.internal.c1"));

        res = rh.executeGetRequest("v2/_search", encodeBasicHeader("custattr", "nagilum"));
        assertThat(res.getBody(), res.getStatusCode(), is(HttpStatus.SC_OK));

        res = rh.executeGetRequest("v3/_search", encodeBasicHeader("custattr", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_FORBIDDEN));

        final String reindex = "{"
            + "\"source\": {"
            + "\"index\": \"starfleet\""
            + "},"
            + "\"dest\": {"
            + "\"index\": \"copysf\""
            + "}"
            + "}";

        res = rh.executePostRequest("_reindex?pretty", reindex, encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("\"total\" : 1"));
        Assert.assertTrue(res.getBody().contains("\"batches\" : 1"));
        Assert.assertTrue(res.getBody().contains("\"failures\" : [ ]"));

        // rest impersonation
        res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("opendistro_security_impersonate_as", "knuddel"),
            encodeBasicHeader("worf", "worf")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("name=knuddel"));
        Assert.assertTrue(res.getBody().contains("attr.internal.test1"));
        Assert.assertFalse(res.getBody().contains("worf"));

        res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("opendistro_security_impersonate_as", "nonexists"),
            encodeBasicHeader("worf", "worf")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_FORBIDDEN));

        res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("opendistro_security_impersonate_as", "notallowed"),
            encodeBasicHeader("worf", "worf")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void testHTTPSCompressionEnabled() throws Exception {
        final Settings settings = Settings.builder()
            .put("plugins.security.ssl.http.enabled", true)
            .put("plugins.security.ssl.http.keystore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
            .put("plugins.security.ssl.http.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
            .put("http.compression", true)
            .build();
        setup(Settings.EMPTY, new DynamicSecurityConfig(), settings, true);
        final RestHelper rh = restHelper(); // ssl resthelper

        HttpResponse res = rh.executeGetRequest("_opendistro/_security/sslinfo", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        assertContains(res, "*ssl_protocol\":\"TLSv1.2*");

        res = rh.executeGetRequest("_nodes", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        assertNotContains(res, "*\"compression\":\"false\"*");
        assertContains(res, "*\"compression\":\"true\"*");
    }

    @Test
    public void testHTTPSCompression() throws Exception {
        final Settings settings = Settings.builder()
            .put("plugins.security.ssl.http.enabled", true)
            .put("plugins.security.ssl.http.keystore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
            .put("plugins.security.ssl.http.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
            .build();
        setup(Settings.EMPTY, new DynamicSecurityConfig(), settings, true);
        final RestHelper rh = restHelper(); // ssl resthelper

        HttpResponse res = rh.executeGetRequest("_opendistro/_security/sslinfo", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        assertContains(res, "*ssl_protocol\":\"TLSv1.2*");

        res = rh.executeGetRequest("_nodes", encodeBasicHeader("nagilum", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        assertContains(res, "*\"compression\":\"false\"*");
        assertNotContains(res, "*\"compression\":\"true\"*");
    }

    @Test
    public void testHTTPAnon() throws Exception {

        setup(Settings.EMPTY, new DynamicSecurityConfig().setConfig("config_anon.yml"), Settings.EMPTY, true);

        RestHelper rh = nonSslRestHelper();

        assertThat(rh.executeGetRequest("").getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("worf", "wrong")).getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("nagilum", "nagilum")).getStatusCode(), is(HttpStatus.SC_OK));

        HttpResponse resc = rh.executeGetRequest("_opendistro/_security/authinfo");
        Assert.assertTrue(resc.getBody().contains("opendistro_security_anonymous"));
        assertThat(resc.getStatusCode(), is(HttpStatus.SC_OK));

        resc = rh.executeGetRequest("_opendistro/_security/authinfo?pretty=true");
        Assert.assertTrue(resc.getBody().contains("\"remote_address\" : \"")); // check pretty print
        assertThat(resc.getStatusCode(), is(HttpStatus.SC_OK));

        resc = rh.executeGetRequest("_opendistro/_security/authinfo", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertTrue(resc.getBody().contains("nagilum"));
        Assert.assertFalse(resc.getBody().contains("opendistro_security_anonymous"));
        assertThat(resc.getStatusCode(), is(HttpStatus.SC_OK));

        try (Client tc = getClient()) {
            tc.index(
                new IndexRequest(".opendistro_security").id("config")
                    .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("config", FileHelper.readYamlContent("config.yml"))
            ).actionGet();
            tc.index(
                new IndexRequest(".opendistro_security").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .id("internalusers")
                    .source("internalusers", FileHelper.readYamlContent("internal_users.yml"))
            ).actionGet();
            ConfigUpdateResponse cur = tc.execute(
                ConfigUpdateAction.INSTANCE,
                new ConfigUpdateRequest(new String[] { "config", "roles", "rolesmapping", "internalusers", "actiongroups" })
            ).actionGet();
            Assert.assertFalse(cur.hasFailures());
            assertThat(cur.getNodes().size(), is(clusterInfo.numNodes));
        }

        assertThat(rh.executeGetRequest("").getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("_opendistro/_security/authinfo").getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("worf", "wrong")).getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("nagilum", "nagilum")).getStatusCode(), is(HttpStatus.SC_OK));
    }

    @Test
    public void testHTTPClientCert() throws Exception {
        final Settings settings = Settings.builder()
            .put("plugins.security.ssl.http.clientauth_mode", "REQUIRE")
            .put("plugins.security.ssl.http.enabled", true)
            .put("plugins.security.ssl.http.keystore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
            .put("plugins.security.ssl.http.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
            .putList(SSLConfigConstants.SECURITY_SSL_HTTP_ENABLED_PROTOCOLS, "TLSv1.1", "TLSv1.2")
            .putList(SSLConfigConstants.SECURITY_SSL_HTTP_ENABLED_CIPHERS, "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256")
            .putList(SSLConfigConstants.SECURITY_SSL_TRANSPORT_ENABLED_PROTOCOLS, "TLSv1.1", "TLSv1.2")
            .putList(SSLConfigConstants.SECURITY_SSL_TRANSPORT_ENABLED_CIPHERS, "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256")
            .build();

        setup(Settings.EMPTY, new DynamicSecurityConfig().setConfig("config_clientcert.yml"), settings, true);

        try (Client tc = getClient()) {

            tc.index(new IndexRequest("vulcangov").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            ConfigUpdateResponse cur = tc.execute(
                ConfigUpdateAction.INSTANCE,
                new ConfigUpdateRequest(new String[] { "config", "roles", "rolesmapping", "internalusers", "actiongroups" })
            ).actionGet();
            Assert.assertFalse(cur.hasFailures());
            assertThat(cur.getNodes().size(), is(clusterInfo.numNodes));
        }

        RestHelper rh = restHelper();

        rh.enableHTTPClientSSL = true;
        rh.trustHTTPServerCertificate = true;
        rh.sendAdminCertificate = true;
        rh.keystore = "spock-keystore.jks";
        assertThat(rh.executeGetRequest("_search").getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(rh.executePutRequest(".opendistro_security/_doc/x", "{}").getStatusCode(), is(HttpStatus.SC_FORBIDDEN));

        rh.keystore = "kirk-keystore.jks";
        assertThat(rh.executePutRequest(".opendistro_security/_doc/y", "{}").getStatusCode(), is(HttpStatus.SC_CREATED));
        assertThat(rh.executeGetRequest("_opendistro/_security/authinfo").getStatusCode(), is(HttpStatus.SC_OK));
    }

    @Test
    @Ignore
    public void testHTTPPlaintextErrMsg() throws Exception {

        try {
            final Settings settings = Settings.builder()
                .put("plugins.security.ssl.http.keystore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
                .put("plugins.security.ssl.http.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
                .put("plugins.security.ssl.http.enabled", true)
                .build();
            setup(settings);
            RestHelper rh = nonSslRestHelper();
            rh.executeGetRequest("", encodeBasicHeader("worf", "worf"));
            Assert.fail("NoHttpResponseException expected");
        } catch (NoHttpResponseException e) {
            String log = Files.readString(new File("unittest.log").toPath(), StandardCharsets.UTF_8);
            Assert.assertTrue(log, log.contains("speaks http plaintext instead of ssl, will close the channel"));
        } catch (Exception e) {
            Assert.fail("NoHttpResponseException expected but was " + e.getClass() + "#" + e.getMessage());
        }

    }

    @Test
    public void testHTTPProxyDefault() throws Exception {
        setup(Settings.EMPTY, new DynamicSecurityConfig().setConfig("config_proxy.yml"), Settings.EMPTY, true);
        RestHelper rh = nonSslRestHelper();

        assertThat(rh.executeGetRequest("").getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("nagilum", "nagilum")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "",
                    new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
                    new BasicHeader("x-proxy-user", "scotty"),
                    encodeBasicHeader("nagilum-wrong", "nagilum-wrong")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "",
                    new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
                    new BasicHeader("x-proxy-user-wrong", "scotty"),
                    encodeBasicHeader("nagilum", "nagilum")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_INTERNAL_SERVER_ERROR,
            is(
                rh.executeGetRequest(
                    "",
                    new BasicHeader("x-forwarded-for", "a"),
                    new BasicHeader("x-proxy-user", "scotty"),
                    encodeBasicHeader("nagilum-wrong", "nagilum-wrong")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_INTERNAL_SERVER_ERROR,
            is(
                rh.executeGetRequest("", new BasicHeader("x-forwarded-for", "a,b,c"), new BasicHeader("x-proxy-user", "scotty"))
                    .getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "",
                    new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
                    new BasicHeader("x-proxy-user", "scotty")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "",
                    new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
                    new BasicHeader("X-Proxy-User", "scotty")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "",
                    new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
                    new BasicHeader("x-proxy-user", "scotty"),
                    new BasicHeader("x-proxy-roles", "starfleet,engineer")
                ).getStatusCode()
            )
        );

    }

    @Test
    public void testHTTPProxyRolesSeparator() throws Exception {
        setup(Settings.EMPTY, new DynamicSecurityConfig().setConfig("config_proxy_custom.yml"), Settings.EMPTY, true);
        RestHelper rh = nonSslRestHelper();
        // separator is configured as ";" so separating roles with "," leads to one (wrong) backend role
        HttpResponse res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
            new BasicHeader("user", "scotty"),
            new BasicHeader("roles", "starfleet,engineer")
        );
        Assert.assertTrue(
            "Expected one backend role since separator is incorrect",
            res.getBody().contains("\"backend_roles\":[\"starfleet,engineer\"]")
        );
        // correct separator, now we should see two backend roles
        res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("x-forwarded-for", "localhost,192.168.0.1,10.0.0.2"),
            new BasicHeader("user", "scotty"),
            new BasicHeader("roles", "starfleet;engineer")
        );
        Assert.assertTrue(
            "Expected two backend roles string since separator is correct: " + res.getBody(),
            res.getBody().contains("\"backend_roles\":[\"starfleet\",\"engineer\"]")
        );

    }

    @Test
    public void testHTTPBasic2() throws Exception {

        setup(Settings.EMPTY, new DynamicSecurityConfig(), Settings.EMPTY);

        try (Client tc = getClient()) {

            tc.admin().indices().create(new CreateIndexRequest("copysf")).actionGet();

            tc.index(new IndexRequest("vulcangov").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            tc.index(new IndexRequest("starfleet").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            tc.index(
                new IndexRequest("starfleet_academy").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();

            tc.index(
                new IndexRequest("starfleet_library").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();

            tc.index(
                new IndexRequest("klingonempire").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();

            tc.index(new IndexRequest("public").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            tc.index(new IndexRequest("spock").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("kirk").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(
                new IndexRequest("role01_role02").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();

            tc.admin()
                .indices()
                .aliases(
                    new IndicesAliasesRequest().addAliasAction(
                        AliasActions.add().indices("starfleet", "starfleet_academy", "starfleet_library").alias("sf")
                    )
                )
                .actionGet();
            tc.admin()
                .indices()
                .aliases(
                    new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("klingonempire", "vulcangov").alias("nonsf"))
                )
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("public").alias("unrestricted")))
                .actionGet();
        }

        RestHelper rh = nonSslRestHelper();

        assertThat(rh.executeGetRequest("").getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("nagilum", "nagilum")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeDeleteRequest("nonexistentindex*", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeGetRequest(".nonexistentindex*", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest(".opendistro_security/_doc/2", "{}", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_NOT_FOUND,
            is(rh.executeGetRequest(".opendistro_security/_doc/0", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_NOT_FOUND,
            is(rh.executeGetRequest("xxxxyyyy/_doc/0", encodeBasicHeader("nagilum", "nagilum")).getStatusCode())
        );
        assertThat(rh.executeGetRequest("", encodeBasicHeader("abc", "abc:abc")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", encodeBasicHeader("userwithnopassword", "")).getStatusCode()));
        assertThat(
            HttpStatus.SC_UNAUTHORIZED,
            is(rh.executeGetRequest("", encodeBasicHeader("userwithblankpassword", "")).getStatusCode())
        );
        assertThat(rh.executeGetRequest("", encodeBasicHeader("worf", "wrongpasswd")).getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(
            HttpStatus.SC_UNAUTHORIZED,
            is(rh.executeGetRequest("", new BasicHeader("Authorization", "Basic " + "wrongheader")).getStatusCode())
        );
        assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", new BasicHeader("Authorization", "Basic ")).getStatusCode()));
        assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", new BasicHeader("Authorization", "Basic")).getStatusCode()));
        assertThat(rh.executeGetRequest("", new BasicHeader("Authorization", "")).getStatusCode(), is(HttpStatus.SC_UNAUTHORIZED));
        assertThat(rh.executeGetRequest("", encodeBasicHeader("picard", "picard")).getStatusCode(), is(HttpStatus.SC_OK));

        for (int i = 0; i < 10; i++) {
            assertThat(HttpStatus.SC_UNAUTHORIZED, is(rh.executeGetRequest("", encodeBasicHeader("worf", "wrongpasswd")).getStatusCode()));
        }

        assertThat(
            HttpStatus.SC_OK,
            is(rh.executePutRequest("/theindex", "{}", encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_CREATED,
            is(
                rh.executePutRequest("/theindex/_doc/1?refresh=true", "{\"a\":0}", encodeBasicHeader("theindexadmin", "theindexadmin"))
                    .getStatusCode()
            )
        );
        // assertThat(HttpStatus.SC_OK,
        // rh.executeGetRequest("/theindex/_analyze?text=this+is+a+test",encodeBasicHeader("theindexadmin",
        // "theindexadmin")).getStatusCode());
        // assertThat(HttpStatus.SC_FORBIDDEN,
        // rh.executeGetRequest("_analyze?text=this+is+a+test",encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode());
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeDeleteRequest("/theindex", encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeDeleteRequest("/klingonempire", encodeBasicHeader("theindexadmin", "theindexadmin")).getStatusCode())
        );
        assertThat(rh.executeGetRequest("starfleet/_search", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_OK));
        assertThat(rh.executeGetRequest("_search", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_FORBIDDEN));
        assertThat(
            HttpStatus.SC_OK,
            is(rh.executeGetRequest("starfleet/_search?pretty", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeDeleteRequest(".opendistro_security/", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePostRequest("/.opendistro_security/_close", null, encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePostRequest("/.opendistro_security/_upgrade", null, encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest("/.opendistro_security/_mapping", "{}", encodeBasicHeader("worf", "worf")).getStatusCode())
        );

        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest(".opendistro_security/", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest(".opendistro_security/_doc/2", "{}", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest(".opendistro_security/_doc/0", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeDeleteRequest(".opendistro_security/_doc/0", encodeBasicHeader("worf", "worf")).getStatusCode())
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executePutRequest(".opendistro_security/_doc/0", "{}", encodeBasicHeader("worf", "worf")).getStatusCode())
        );

        HttpResponse resc = rh.executeGetRequest("_cat/indices/public", encodeBasicHeader("bug108", "nagilum"));
        // Assert.assertTrue(resc.getBody().contains("green"));
        assertThat(resc.getStatusCode(), is(HttpStatus.SC_OK));

        assertThat(
            HttpStatus.SC_OK,
            is(
                rh.executeGetRequest(
                    "role01_role02/_search?pretty",
                    encodeBasicHeader("user_role01_role02_role03", "user_role01_role02_role03")
                ).getStatusCode()
            )
        );
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest("role01_role02/_search?pretty", encodeBasicHeader("user_role01", "user_role01")).getStatusCode())
        );

        assertThat(HttpStatus.SC_OK, is(rh.executeGetRequest("spock/_search?pretty", encodeBasicHeader("spock", "spock")).getStatusCode()));
        assertThat(
            HttpStatus.SC_FORBIDDEN,
            is(rh.executeGetRequest("spock/_search?pretty", encodeBasicHeader("kirk", "kirk")).getStatusCode())
        );
        assertThat(HttpStatus.SC_OK, is(rh.executeGetRequest("kirk/_search?pretty", encodeBasicHeader("kirk", "kirk")).getStatusCode()));

        // all

    }

    @Test
    public void testBulk() throws Exception {
        final Settings settings = Settings.builder().put(ConfigConstants.SECURITY_ROLES_MAPPING_RESOLUTION, "BOTH").build();
        setup(Settings.EMPTY, new DynamicSecurityConfig().setSecurityRoles("roles_bulk.yml"), settings);
        final RestHelper rh = nonSslRestHelper();

        String bulkBody = "{ \"index\" : { \"_index\" : \"test\", \"_id\" : \"1\" } }"
            + System.lineSeparator()
            + "{ \"field1\" : \"value1\" }"
            + System.lineSeparator()
            + "{ \"index\" : { \"_index\" : \"test\", \"_id\" : \"2\" } }"
            + System.lineSeparator()
            + "{ \"field2\" : \"value2\" }"
            + System.lineSeparator();

        HttpResponse res = rh.executePostRequest("_bulk", bulkBody, encodeBasicHeader("bulk", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("\"errors\":false"));
        Assert.assertTrue(res.getBody().contains("\"status\":201"));
    }

    @Test
    public void testBulkWithOneIndexFailure() throws Exception {
        final Settings settings = Settings.builder().put(ConfigConstants.SECURITY_ROLES_MAPPING_RESOLUTION, "BOTH").build();
        setup(Settings.EMPTY, new DynamicSecurityConfig().setSecurityRoles("roles_bulk.yml"), settings);
        final RestHelper rh = nonSslRestHelper();

        String bulkBody = "{ \"index\" : { \"_index\" : \"test\", \"_id\" : \"1\" } }"
            + System.lineSeparator()
            + "{ \"a\" : \"b\" }"
            + System.lineSeparator()
            + "{ \"index\" : { \"_index\" : \"myindex\", \"_id\" : \"1\" } }"
            + System.lineSeparator()
            + "{ \"a\" : \"b\" }"
            + System.lineSeparator();

        HttpResponse res = rh.executePostRequest("_bulk?refresh=true", bulkBody, encodeBasicHeader("bulk_test_user", "nagilum"));
        JsonNode jsonNode = readTree(res.getBody());
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(jsonNode.get("errors").booleanValue());
        assertThat(jsonNode.get("items").get(0).get("index").get("status").intValue(), is(201));
        assertThat(jsonNode.get("items").get(1).get("index").get("status").intValue(), is(403));
    }

    @Test
    public void test557() throws Exception {
        final Settings settings = Settings.builder().put(ConfigConstants.SECURITY_ROLES_MAPPING_RESOLUTION, "BOTH").build();
        setup(Settings.EMPTY, new DynamicSecurityConfig(), settings);

        try (Client tc = getClient()) {

            tc.admin().indices().create(new CreateIndexRequest("copysf")).actionGet();

            tc.index(new IndexRequest("vulcangov").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            tc.index(new IndexRequest("starfleet").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();

            tc.index(
                new IndexRequest("starfleet_academy").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)
            ).actionGet();

        }

        final RestHelper rh = nonSslRestHelper();

        HttpResponse res = rh.executePostRequest(
            "/*/_search",
            "{\"size\":0,\"aggs\":{\"indices\":{\"terms\":{\"field\":\"_index\",\"size\":10}}}}",
            encodeBasicHeader("nagilum", "nagilum")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("starfleet_academy"));
        res = rh.executePostRequest(
            "/*/_search",
            "{\"size\":0,\"aggs\":{\"indices\":{\"terms\":{\"field\":\"_index\",\"size\":10}}}}",
            encodeBasicHeader("557", "nagilum")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("starfleet_academy"));
    }

    @Test
    public void testITT1635() throws Exception {
        final Settings settings = Settings.builder().put(ConfigConstants.SECURITY_ROLES_MAPPING_RESOLUTION, "BOTH").build();
        setup(Settings.EMPTY, new DynamicSecurityConfig().setConfig("config_dnfof.yml").setSecurityRoles("roles_itt1635.yml"), settings);

        try (Client tc = getClient()) {

            tc.index(new IndexRequest("esb-prod-1").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("esb-prod-2").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":2}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("esb-prod-3").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":3}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("esb-prod-4").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":4}", XContentType.JSON))
                .actionGet();
            tc.index(new IndexRequest("esb-prod-5").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":5}", XContentType.JSON))
                .actionGet();

            tc.admin()
                .indices()
                .aliases(
                    new IndicesAliasesRequest().addAliasAction(
                        AliasActions.add()
                            .indices("esb-prod-1", "esb-prod-2", "esb-prod-3", "esb-prod-4", "esb-prod-5")
                            .alias("esb-prod-all")
                    )
                )
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("esb-prod-1").alias("esb-alias-1")))
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("esb-prod-2").alias("esb-alias-2")))
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("esb-prod-3").alias("esb-alias-3")))
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("esb-prod-4").alias("esb-alias-4")))
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("esb-prod-5").alias("esb-alias-5")))
                .actionGet();

        }

        final RestHelper rh = nonSslRestHelper();

        HttpResponse res = rh.executeGetRequest("/esb-prod-*/_search?pretty", encodeBasicHeader("itt1635", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        res = rh.executeGetRequest("/esb-alias-*/_search?pretty", encodeBasicHeader("itt1635", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        res = rh.executeGetRequest("/esb-prod-all/_search?pretty", encodeBasicHeader("itt1635", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
    }

    @Test
    public void testTenantInfo() throws Exception {
        final Settings settings = Settings.builder().build();
        setup(Settings.EMPTY, new DynamicSecurityConfig(), settings);

        /*

            [admin_1, praxisrw, abcdef_2_2, kltentro, praxisro, kltentrw]
        	admin_1==.kibana_-1139640511_admin1
        	praxisrw==.kibana_-1386441176_praxisrw
        	abcdef_2_2==.kibana_-634608247_abcdef22
        	kltentro==.kibana_-2014056171_kltentro
        	praxisro==.kibana_-1386441184_praxisro
        	kltentrw==.kibana_-2014056163_kltentrw

         */

        try (Client tc = getClient()) {

            tc.index(new IndexRequest(".kibana-6").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":2}", XContentType.JSON))
                .actionGet();
            tc.index(
                new IndexRequest(".kibana_-1139640511_admin1").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("{\"content\":3}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest(".kibana_-1386441176_praxisrw").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("{\"content\":3}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest(".kibana_-634608247_abcdef22").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("{\"content\":3}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest(".kibana_-12345_123456").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("{\"content\":3}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest(".kibana2_-12345_123456").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("{\"content\":3}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest(".kibana_9876_xxx_ccc").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .source("{\"content\":3}", XContentType.JSON)
            ).actionGet();
            tc.index(
                new IndexRequest(".kibana_fff_eee").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":3}", XContentType.JSON)
            ).actionGet();

            tc.index(new IndexRequest("esb-prod-5").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":5}", XContentType.JSON))
                .actionGet();

            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices(".kibana-6").alias(".kibana")))
                .actionGet();
            tc.admin()
                .indices()
                .aliases(
                    new IndicesAliasesRequest().addAliasAction(
                        AliasActions.add().indices("esb-prod-5").alias(".kibana_-2014056163_kltentrw")
                    )
                )
                .actionGet();
            tc.admin()
                .indices()
                .aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("esb-prod-5").alias("esb-alias-5")))
                .actionGet();

        }

        final RestHelper rh = nonSslRestHelper();

        HttpResponse res = rh.executeGetRequest("_opendistro/_security/tenantinfo?pretty", encodeBasicHeader("itt1635", "nagilum"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_FORBIDDEN));

        res = rh.executeGetRequest("_opendistro/_security/tenantinfo?pretty", encodeBasicHeader("kibanaserver", "kibanaserver"));
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("\".kibana_-1139640511_admin1\" : \"admin_1\""));
        Assert.assertTrue(res.getBody().contains("\".kibana_-1386441176_praxisrw\" : \"praxisrw\""));
        Assert.assertTrue(res.getBody().contains(".kibana_-2014056163_kltentrw\" : \"kltentrw\""));
        Assert.assertTrue(res.getBody().contains("\".kibana_-634608247_abcdef22\" : \"abcdef_2_2\""));
        Assert.assertTrue(res.getBody().contains("\".kibana_-12345_123456\" : \"__private__\""));
        Assert.assertFalse(res.getBody().contains(".kibana-6"));
        Assert.assertFalse(res.getBody().contains("esb-"));
        Assert.assertFalse(res.getBody().contains("xxx"));
        Assert.assertFalse(res.getBody().contains(".kibana2"));
    }

    @Test
    public void testRestImpersonation() throws Exception {
        final Settings settings = Settings.builder()
            .putList(ConfigConstants.SECURITY_AUTHCZ_REST_IMPERSONATION_USERS + ".worf", "someotherusernotininternalusersfile")
            .build();
        setup(Settings.EMPTY, new DynamicSecurityConfig().setConfig("config_rest_impersonation.yml"), settings);
        final RestHelper rh = nonSslRestHelper();

        // rest impersonation
        HttpResponse res = rh.executeGetRequest(
            "_opendistro/_security/authinfo",
            new BasicHeader("opendistro_security_impersonate_as", "someotherusernotininternalusersfile"),
            encodeBasicHeader("worf", "worf")
        );
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
        Assert.assertTrue(res.getBody().contains("name=someotherusernotininternalusersfile"));
        Assert.assertFalse(res.getBody().contains("worf"));
    }

    @Test
    public void testSslOnlyMode() throws Exception {
        final Settings settings = Settings.builder().put(ConfigConstants.SECURITY_SSL_ONLY, true).build();
        setupSslOnlyMode(settings);
        final RestHelper rh = nonSslRestHelper();

        HttpResponse res = rh.executeGetRequest("_opendistro/_security/sslinfo");
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));

        res = rh.executePutRequest("/xyz/_doc/1", "{\"a\":5}");
        assertThat(res.getStatusCode(), is(HttpStatus.SC_CREATED));

        res = rh.executeGetRequest("/_mappings");
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));

        res = rh.executeGetRequest("/_search");
        assertThat(res.getStatusCode(), is(HttpStatus.SC_OK));
    }

    @Test
    public void testAll() throws Exception {
        final Settings settings = Settings.builder().build();
        setup(settings);
        final RestHelper rh = nonSslRestHelper();

        try (Client tc = getClient()) {
            tc.index(new IndexRequest("abcdef").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON))
                .actionGet();
        }

        assertThat(HttpStatus.SC_FORBIDDEN, is(rh.executeGetRequest("_all/_search", encodeBasicHeader("worf", "worf")).getStatusCode()));
        assertThat(rh.executeGetRequest("*/_search", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_FORBIDDEN));
        assertThat(rh.executeGetRequest("_search", encodeBasicHeader("worf", "worf")).getStatusCode(), is(HttpStatus.SC_FORBIDDEN));
    }

}
