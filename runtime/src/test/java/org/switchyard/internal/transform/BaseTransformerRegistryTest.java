/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */

package org.switchyard.internal.transform;

import javax.xml.namespace.QName;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.switchyard.metadata.java.JavaService;
import org.switchyard.transform.BaseTransformer;
import org.switchyard.transform.Transformer;
import org.switchyard.transform.TransformerRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BaseTransformerRegistryTest {
    
    private TransformerRegistry _registry;
    
    @Before
    public void setUp() throws Exception {
        _registry = new BaseTransformerRegistry();
    }
    
    @Test
    public void testAddGetTransformer() {
        final QName fromName = new QName("a");
        final QName toName = new QName("b");
        
        BaseTransformer<String, Integer> t = 
            new BaseTransformer<String, Integer>(fromName, toName) {
                public Integer transform(String from) {
                    return null;
                }
        };
        
        _registry.addTransformer(t);
        Assert.assertEquals(t, _registry.getTransformer(fromName, toName));      
    }

    @Test
    public void test_fallbackTransformerComparator_resolvable() {
        List<BaseTransformerRegistry.JavaSourceFallbackTransformer> transformersList = new ArrayList<BaseTransformerRegistry.JavaSourceFallbackTransformer>();

        // Mix them up when inserting
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(C.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(E.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(D.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(A.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(B.class, null));

        BaseTransformerRegistry.JavaSourceFallbackTransformerComparator comparator = new BaseTransformerRegistry.JavaSourceFallbackTransformerComparator();
        Collections.sort(transformersList, comparator);

        // Should be sorted, sub-types first...
        Assert.assertTrue(transformersList.get(0).getJavaType() == E.class);
        Assert.assertTrue(transformersList.get(1).getJavaType() == D.class);
        Assert.assertTrue(transformersList.get(2).getJavaType() == C.class);
        Assert.assertTrue(transformersList.get(3).getJavaType() == B.class);
        Assert.assertTrue(transformersList.get(4).getJavaType() == A.class);
    }

    @Test
    public void test_fallbackTransformerComparator_unresolvable() {
        List<BaseTransformerRegistry.JavaSourceFallbackTransformer> transformersList = new ArrayList<BaseTransformerRegistry.JavaSourceFallbackTransformer>();

        // Mix them up when inserting
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(C.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(E.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(D.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(A.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(B.class, null));
        transformersList.add(new BaseTransformerRegistry.JavaSourceFallbackTransformer(I.class, null)); // branch

        BaseTransformerRegistry.JavaSourceFallbackTransformerComparator comparator = new BaseTransformerRegistry.JavaSourceFallbackTransformerComparator();
        try {
            Collections.sort(transformersList, comparator);
            Assert.fail("Expected RuntimeException.");
        } catch(RuntimeException e) {
            Assert.assertEquals("Multiple possible fallback types 'java:org.switchyard.internal.transform.BaseTransformerRegistryTest$A' and 'java:org.switchyard.internal.transform.BaseTransformerRegistryTest$I'.", e.getMessage());
        }
    }

    @Test
    public void test_getFallbackTransformer_resolvable() {
        addTransformer(B.class);
        addTransformer(C.class);
        addTransformer(A.class);

        Transformer<?,?> transformer;

        // Should return no transformer...
        transformer = _registry.getTransformer(getType(D.class), new QName("targetX"));
        Assert.assertNull(transformer);

        // Should return the C transformer...
        transformer = _registry.getTransformer(getType(D.class), new QName("target1"));
        Assert.assertNotNull(transformer);
        Assert.assertEquals(getType(C.class), transformer.getFrom());

        transformer = _registry.getTransformer(getType(D.class), new QName("target1"));
    }

    @Test
    public void test_getFallbackTransformer_unresolvable() {
        addTransformer(B.class);
        addTransformer(C.class);
        addTransformer(A.class);
        addTransformer(I.class);

        Transformer<?,?> transformer;

        // Should return no transformer...
        transformer = _registry.getTransformer(getType(D.class), new QName("targetX"));
        Assert.assertNull(transformer);

        // Should return no transformer because the existence of the I transformer makes a
        // unique lookup impossible...
        transformer = _registry.getTransformer(getType(D.class), new QName("target1"));
        Assert.assertNull(transformer);
    }

    private void addTransformer(Class<?> type) {
        QName fromType = getType(type);
        QName toType = new QName("target1");
        _registry.addTransformer(new TestTransformer(fromType, toType));
    }

    private QName getType(Class<?> type) {
        return JavaService.toMessageType(type);
    }

    public class A {}
    public class B extends A {}
    public class C extends B {}
    public class D extends C implements I {}
    public class E extends D {}
    public interface I {}

    public class TestTransformer extends BaseTransformer {
        public TestTransformer(QName from, QName to) {
            super(from, to);
        }

        @Override
        public Object transform(Object from) {
            return from;
        }
    }
}
