package com.thoughtworks.mockpico;
/**
 * Copyright (c) 2010 ThoughtWorks
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.NoInteractionsWanted;
import org.picocontainer.MutablePicoContainer;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sun.tools.internal.ws.wsdl.parser.Util.fail;
import static com.thoughtworks.mockpico.Mockpico.injectionAnnotation;
import static com.thoughtworks.mockpico.Mockpico.makePicoContainer;
import static com.thoughtworks.mockpico.Mockpico.mockDepsFor;
import static com.thoughtworks.mockpico.Mockpico.resetAll;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.picocontainer.injectors.Injectors.CDI;

public class MockpicoTestCase {

    private static C c = new C();  // represented by C* in asserts
    private static D d = new D();  // represented by D* in asserts
    private static B b = new B(c); // represented by B*<C*> in asserts

    @Test
    public void testCanMockConstructorAndSetterDepsWhenNotInjected() {

        A a = mockDepsFor(A.class)
                .withSetters()
                .make();

        assertEquals("A(mock[C]#0,mock[B]#1)setIt(mock[D]#2)", a.toString());
    }

    @Test
    public void testCanUseRealConstructorAndSetterDepsWhenInjected() {

        A a = mockDepsFor(A.class)
                .withSetters()
                .withInjectees(b, c, d)
                .make();

        assertEquals("A(C*,B*<C*>)setIt(D*)", a.toString());
    }

    @Test
    public void testPicoCanMakeFromTypesAndCacheDeps() {

        A a = mockDepsFor(A.class)
                .withSetters()
                .withInjectees(B.class, C.class, D.class)
                .make();

        assertEquals("A(C#0,B#1<C#0>)setIt(D#2)", a.toString());
    }

    @Test
    public void testCanSetterInjectionIsNotDefault() {

        A a = mockDepsFor(A.class)
                .withInjectees(b, c)
                .make();

        assertEquals("A(C*,B*<C*>)inj3ct(B*<C*>)aut0wireMe(B*<C*>)", a.toString());
    }


    @Test
    public void testCanSpecifyConstructorInjectionOnly() {

        A a = mockDepsFor(A.class)
                .withInjectionTypes(CDI())
                .withInjectees(b, c, d)
                .make();

        assertEquals("A(C*,B*<C*>)", a.toString());
    }

    @Test
    public void testCanUseAPicoContainerHandedInAndJournalInjectionsToSpecialObject() {
        MutablePicoContainer pico = makePicoContainer();

        A a = mockDepsFor(A.class)
                .using(pico)
                .make();

        assertEquals("A(mock[C]#0,mock[B]#1)setIt(mock[D]#2)", a.toString());

        String actual = pico.getComponent(Mockpico.Journal.class).toString();
        assertTrue(actual.indexOf("Constructor being injected:") > -1);
        assertTrue(actual.indexOf("  arg[0] type:class com.thoughtworks.mockpico.MockpicoTestCase$C, with: Mock for C, hashCode: ") > 0);
        assertTrue(actual.indexOf("  arg[1] type:class com.thoughtworks.mockpico.MockpicoTestCase$B, with: Mock for B, hashCode: ") > 0);
        assertTrue(actual.indexOf("Method being injected: 'setIt' with: Mock for D, hashCode: ") > 0);
    }

    @Test
    public void testCanMockConstructorAndDefaultInjecteesWhenNotSupplied() {

        A a = mockDepsFor(A.class)
                .make();

        assertEquals("A(mock[C]#0,mock[B]#1)inj3ct(mock[B]#1)aut0wireMe(mock[B]#1)", a.toString());
    }

    @Test
    public void testCanUseMocksPassedIn() {
        List list1 = mock(List.class);

        NeedsList nl = mockDepsFor(NeedsList.class)
                .withInjectees(list1)
                .make();

        assertSame(list1, nl.list);
    }

    @Test
    public void testCanMockPrimivitesAndAlsoUseCustomAnnotation() {

        A bc = mockDepsFor(A.class)
                .using(makePicoContainer(CDI(), injectionAnnotation(A.Foobarred.class)))
                .make();

        assertEquals("A(mock[C]#0,mock[B]#1)foobar(String#0,Integer#1,Double#2,Double#2,Float#3,Byte#4,Short#5,mock[BigInteger]#2,Character#6,Long#7)", bc.toString());
    }

    @Test
    public void verifyNoMoreInteractionsCanBePercolated() {
        MutablePicoContainer mocks = makePicoContainer();

        NeedsList nl = mockDepsFor(NeedsList.class)
                .using(mocks)
                .make();

        nl.oops();
        try {
            Mockpico.verifyNoMoreInteractionsForAll(mocks);
            fail("should have barfed");
        } catch (NoInteractionsWanted e) {
            // expected  
        }
    }

    @Test
    public void resetCanBePercolated() {
        MutablePicoContainer mocks = makePicoContainer();

        NeedsList nl = mockDepsFor(NeedsList.class)
                .using(mocks)
                .make();

        nl.oops();
        resetAll(mocks);
        Mockito.verifyNoMoreInteractions(mocks.getComponent(List.class));
    }

    public static class NeedsList {
        private List list;

        public NeedsList(List list) {
            this.list = list;
        }

        public void oops() {
            list.add("oops");
        }

    }


    public static class A {

        private StringBuilder sb = new StringBuilder();
        private Map<Object, String> printed = new HashMap<Object, String>();
        private int mocks;
        private int reals;

        @Override
        public String toString() {
            return sb.toString();
        }

        private String prt(Object obj) {
            String p = printed.get(obj);
            if (p == null) {
                if (obj.toString().indexOf("Mock for") > -1) {
                    Class<?> parent = obj.getClass().getSuperclass();
                    if (parent == Object.class) {
                        parent = obj.getClass().getInterfaces()[0];
                    }
                    p = "mock["+ parent.getName().substring(parent.getName().lastIndexOf('.')+1).replace("MockpicoTestCase$", "") +"]#" + mocks++;
                } else {
                    String name = obj.getClass().getName();
                    if (obj == b) {
                        return "B*<C*>";
                    } else if (obj == c) {
                        return "C*";
                    } else if (obj == d) {
                        return "D*";
                    }
                    p = name.substring(name.lastIndexOf(".")+1).replace("MockpicoTestCase$", "") + "#" + reals++;
                    if (obj instanceof B) {
                        p = p +  "<" + prt(((B) obj).c) + ">";
                    }
                }
                printed.put(obj, p);
            }
            return p;
        }


        public A(C c, B b) {
            sb.append("A(" + prt(c) + "," + prt(b) + ")");
        }

        public void setIt(D d) {
            sb.append("setIt(" + prt(d) + ")");
        }

        @Inject
        public void inj3ct(B b) {
            sb.append("inj3ct(" + prt(b) + ")");
        }

        @Autowired
        public void aut0wireMe(B b) {
            sb.append("aut0wireMe(" + prt(b) + ")");
        }

        @Foobarred
        public void foobar(String str, int iint,
                           double dbl,
                           Double dbl2,
                           boolean bool,
                           float flt,
                           byte byt,
                           short shrt,
                           BigInteger bigInt,
                           char chr,
                           Long lng) {

            sb.append("foobar(" + prt(str) + "," +
                    prt(iint) + "," +
                    prt(dbl) + "," +
                    prt(dbl2) + "," +
                    prt(flt) + "," +
                    prt(byt) + "," +
                    prt(shrt) + "," +
                    prt(bigInt) + "," +
                    prt(chr) + "," +
                    prt(lng) +
                    ")");
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
        public static @interface Foobarred {
        }
    }

    public static class C {
    }

    public static class D {
    }

    public static class B {
        private final C c;

        public B(C c) {
            this.c = c;
        }
    }

}
