/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.jsonldjava.jena;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.iterator.Action;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.lib.Chars;
import org.apache.jena.iri.IRI;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.writer.WriterDatasetRIOTBase;

import com.github.jsonldjava.core.JsonLdApi;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.core.RDFDataset;
import com.github.jsonldjava.utils.JSONUtils;
import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.core.DatasetGraph;
import com.hp.hpl.jena.sparql.util.Context;
import com.hp.hpl.jena.vocabulary.RDF;

class JsonLDWriter extends WriterDatasetRIOTBase {
    private final RDFFormat format;

    public JsonLDWriter(RDFFormat syntaxForm) {
        format = syntaxForm;
    }

    @Override
    public Lang getLang() {
        return format.getLang();
    }

    @Override
    public void write(Writer out, DatasetGraph dataset, PrefixMap prefixMap, String baseURI,
            Context context) {
        serialize(out, dataset, prefixMap, baseURI);
    }

    private boolean isPretty() {
        return RDFFormat.PRETTY.equals(format.getVariant());
    }

    @Override
    public void write(OutputStream out, DatasetGraph dataset, PrefixMap prefixMap, String baseURI,
            Context context) {
        final Writer w = new OutputStreamWriter(out, Chars.charsetUTF8);
        write(w, dataset, prefixMap, baseURI, context);
        IO.flush(w);
    }

    private void serialize(Writer writer, DatasetGraph dataset, PrefixMap prefixMap, String baseURI) {
        final Map<String, Object> ctx = new LinkedHashMap<String, Object>();
        addProperties(ctx, dataset.getDefaultGraph());
        addPrefixes(ctx, prefixMap);

        try {
            final JsonLdOptions opts = new JsonLdOptions(baseURI);
            // opts.graph = false;
            // opts.addBlankNodeIDs = false;
            opts.setUseRdfType(true);
            opts.setUseNativeTypes(true);
            // opts.skipExpansion = false;
            opts.setCompactArrays(true);
            // opts.keepFreeFloatingNodes = false;
            final JsonLdApi api = new JsonLdApi(opts);
            final JenaRDFParser parser = new JenaRDFParser();
            final RDFDataset result = parser.parse(dataset);
            Object obj = api.fromRDF(result);
            final Map<String, Object> localCtx = new HashMap<String, Object>();
            localCtx.put("@context", ctx);

            // TODO: How/when to do simplify vs compact?
            // if (false)
            // obj = JSONLD.simplify(obj, opts);
            // else
            // Unclear as to the way to set better printing.
            obj = JsonLdProcessor.compact(obj, localCtx, opts);

            if (isPretty()) {
                JSONUtils.writePrettyPrint(writer, obj);
            } else {
                JSONUtils.write(writer, obj);
            }
        } catch (final IOException e) {
            throw new RiotException("Could not write JSONLD: " + e, e);
        } catch (final JsonLdError e) {
            throw new RiotException("Could not process JSONLD: " + e, e);
        }
    }

    private static void addPrefixes(Map<String, Object> ctx, PrefixMap prefixMap) {
        final Map<String, IRI> pmap = prefixMap.getMapping();
        for (final Entry<String, IRI> e : pmap.entrySet()) {
            ctx.put(e.getKey(), e.getValue().toString());
        }

    }

    private void addProperties(final Map<String, Object> ctx, Graph graph) {
        // Add some properties directly so it becomes "localname": ....
        final Set<String> dups = new HashSet<String>();
        final Action<Triple> x = new Action<Triple>() {
            @Override
            public void apply(Triple item) {
                final Node p = item.getPredicate();
                if (p.equals(RDF.type.asNode())) {
                    return;
                }
                final String x = p.getLocalName();
                if (dups.contains(x)) {
                    return;
                }

                if (ctx.containsKey(x)) {
                    // Check different URI
                    // pmap2.remove(x) ;
                    // dups.add(x) ;
                } else {
                    final Map<String, Object> x2 = new LinkedHashMap<String, Object>();
                    x2.put("@id", p.getURI());
                    x2.put("@type", "@id");
                    ctx.put(x, x2);
                }
            }
        };

        Iter.iter(graph.find(null, null, null)).apply(x);

    }
}
