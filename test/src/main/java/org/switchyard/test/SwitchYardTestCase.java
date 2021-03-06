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

package org.switchyard.test;

import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.switchyard.ExchangeHandler;
import org.switchyard.ServiceDomain;
import org.switchyard.config.model.MergeScanner;
import org.switchyard.config.model.Model;
import org.switchyard.config.model.ModelResource;
import org.switchyard.config.model.Models;
import org.switchyard.config.model.Scanner;
import org.switchyard.config.model.ScannerInput;
import org.switchyard.config.model.switchyard.SwitchYardModel;
import org.switchyard.config.model.switchyard.v1.V1SwitchYardModel;
import org.switchyard.config.model.transform.TransformModel;
import org.switchyard.config.util.classpath.ClasspathScanner;
import org.switchyard.deploy.internal.AbstractDeployment;
import org.switchyard.deploy.internal.Deployment;
import org.switchyard.metadata.InOnlyService;
import org.switchyard.metadata.InOutService;
import org.switchyard.metadata.ServiceInterface;
import org.switchyard.transform.BaseTransformer;
import org.switchyard.transform.Transformer;
import org.switchyard.transform.config.model.TransformerFactory;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base class for writing SwitchYard tests.
 * <p/>
 * This class creates a {@link ServiceDomain} instance (via an {@link AbstractDeployment}) for your TestCase.  It
 * can be configured via the following annotations:
 * <ul>
 * <li>{@link TestMixIns}: This annotation allows you to "mix-in" the test behavior that your test requires
 * by listing {@link TestMixIn} types.  See the {@link org.switchyard.test.mixins} package for a list of the
 * {@link TestMixIn TestMixIns} available out of the box.  You can also implement your own {@link TestMixIn TestMixIn}.
 * (See {@link #getMixIn(Class)})</li>
 * <li>{@link SwitchYardDeploymentConfig}: Allows you to specify a SwitchYard application configuration file (switchyard.xml) to
 * be used to initialize your TestCase instance {@link ServiceDomain}.</li>
 * </ul>
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class SwitchYardTestCase {

    /**
     * Test configuration model.
     */
    private SwitchYardModel _configModel;
    /**
     * The deployment.
     */
    private AbstractDeployment _deployment;
    /**
     * Test Mix-Ins.
     */
    private List<Class<? extends TestMixIn>> _testMixIns;
    private List<TestMixIn> _testMixInInstances = new ArrayList<TestMixIn>();

    /**
     * Public default constructor.
     */
    public SwitchYardTestCase() {
        SwitchYardDeploymentConfig deploymentConfig = getClass().getAnnotation(SwitchYardDeploymentConfig.class);
        if (deploymentConfig != null && deploymentConfig.value() != null) {
            _configModel = createSwitchYardModel(getClass().getResourceAsStream(deploymentConfig.value()), createScanners(deploymentConfig));
        }

        TestMixIns testMixInsAnnotation = getClass().getAnnotation(TestMixIns.class);
        if (testMixInsAnnotation != null) {
            _testMixIns = Arrays.asList(testMixInsAnnotation.value());
        }
    }

    /**
     * Public constructor.
     * @param configModel Configuration model stream.
     */
    public SwitchYardTestCase(InputStream configModel) {
        SwitchYardDeploymentConfig deploymentConfig = getClass().getAnnotation(SwitchYardDeploymentConfig.class);
        _configModel = createSwitchYardModel(configModel, createScanners(deploymentConfig));
    }

    /**
     * Public constructor.
     * <p/>
     * Loads the config model from the classpath.
     *
     * @param configModelPath Configuration model classpath path.
     */
    public SwitchYardTestCase(String configModelPath) {
        Assert.assertNotNull("Test 'configModel' is null.", configModelPath);
        SwitchYardDeploymentConfig deploymentConfig = getClass().getAnnotation(SwitchYardDeploymentConfig.class);
        _configModel = createSwitchYardModel(getClass().getResourceAsStream(configModelPath), createScanners(deploymentConfig));
    }

    /**
     * Public constructor.
     * @param configModel Configuration model.
     */
    public SwitchYardTestCase(SwitchYardModel configModel) {
        Assert.assertNotNull("Test 'configModel' is null.", configModel);
        _configModel = configModel;
    }

    /**
     * Get the configuration model driving this test instance, if one exists.
     * <p/>
     * An abstract deployment is created if no configuration model is supplied on construction.
     *
     * @return The config model, or null if no config model was used to construct the TestCase instance.
     */
    public SwitchYardModel getConfigModel() {
        return _configModel;
    }

    /**
     * Create and initialise the deployment.
     * @throws Exception creating the deployment.
     */
    @Before
    public final void deploy() throws Exception {
        MockInitialContextFactory.install();
        createMixInInstances();
        mixInSetup();
        _deployment = createDeployment();
        _deployment.init();
        mixInBefore();
        _deployment.start();
    }

    /**
     * Undeploy the deployment.
     */
    @After
    public final void undeploy() {
        assertDeployed();
        _deployment.stop();
        mixInAfter();
        _deployment.destroy();
        mixInTearDown();
        MockInitialContextFactory.clear();
    }

    /**
     * Create the deployment instance.
     * @return The deployment instance.
     * @throws Exception creating the deployment.
     */
    protected AbstractDeployment createDeployment() throws Exception {
        if (_configModel != null) {
            return new Deployment(_configModel);
        } else {
            return new SimpleTestDeployment();
        }
    }

    /**
     * Get the ServiceDomain.
     * @return The service domain.
     */
    public ServiceDomain getServiceDomain() {
        assertDeployed();
        return _deployment.getDomain();
    }

    /**
     * Register an IN_OUT Service.
     * <p/>
     * Registers a {@link MockHandler} as the service handler.
     *
     * @param serviceName The Service name.
     * @return The {@link MockHandler} service handler.
     */
    protected MockHandler registerInOutService(String serviceName) {
        MockHandler handler = new MockHandler();
        getServiceDomain().registerService(QName.valueOf(serviceName), handler, new InOutService());
        return handler;
    }

    /**
     * Register an IN_OUT Service.
     *
     * @param serviceName The Service name.
     * @param serviceHandler The service handler.
     */
    protected void registerInOutService(String serviceName, ExchangeHandler serviceHandler) {
        getServiceDomain().registerService(QName.valueOf(serviceName), serviceHandler, new InOutService());
    }

    /**
     * Register an IN_OUT Service.
     *
     * @param serviceName The Service name.
     * @param serviceHandler The service handler.
     * @param metadata Service interface.
     */
    protected void registerInOutService(String serviceName, ExchangeHandler serviceHandler, ServiceInterface metadata) {
        getServiceDomain().registerService(QName.valueOf(serviceName), serviceHandler, metadata);
    }

    /**
     * Register an IN_ONLY Service.
     * <p/>
     * Registers a {@link MockHandler} as the fault service handler.
     *
     * @param serviceName The Service name.
     * @return The {@link MockHandler} service fault handler.
     */
    protected MockHandler registerInOnlyService(String serviceName) {
        MockHandler handler = new MockHandler();
        getServiceDomain().registerService(QName.valueOf(serviceName), handler, new InOnlyService());
        return handler;
    }

    /**
     * Register an IN_ONLY Service.
     *
     * @param serviceName The Service name.
     * @param serviceHandler The service handler.
     */
    protected void registerInOnlyService(String serviceName, ExchangeHandler serviceHandler) {
        getServiceDomain().registerService(QName.valueOf(serviceName), serviceHandler, new InOnlyService());
    }

    /**
     * Add a Transformer instance.
     * @param transformer The transformer instance.
     */
    public void addTransformer(Transformer transformer) {
        getServiceDomain().getTransformerRegistry().addTransformer(transformer);
    }

    /**
     * Create a new {@link Invoker} instance for invoking a Service in the test ServiceDomain.
     * @param serviceName The target Service name.
     * @return The invoker instance.
     */
    protected Invoker newInvoker(QName serviceName) {
        return new Invoker(getServiceDomain(), serviceName);
    }

    /**
     * Create a new {@link Invoker} instance for invoking a Service in the test ServiceDomain.
     * @param serviceName The target Service name.  Can be a serialized {@link QName}.  Can also
     * include the operation name e.g. "OrderManagementService.createOrder".
     * @return The invoker instance.
     */
    protected Invoker newInvoker(String serviceName) {
        return new Invoker(getServiceDomain(), serviceName);
    }

    /**
     * Create a new {@link Transformer} instance from the specified {@link TransformModel}.
     * @param transformModel The TransformModel.
     * @return The Transformer instance.
     */
    public Transformer newTransformer(TransformModel transformModel) {
        return TransformerFactory.newTransformer(transformModel);
    }

    /**
     * Create a new {@link Transformer} instance from the specified {@link TransformModel} and
     * register it with the test ServiceDomain.
     * @param transformModel The TransformModel.
     * @return The Transformer instance.
     */
    public Transformer registerTransformer(TransformModel transformModel) {
        if (transformModel.getFrom() == null || transformModel.getTo() == null) {
            Assert.fail("Invalid TransformModel instance.  Must specify 'from' and 'to' data types.");
        }

        Transformer<?,?> transformer = TransformerFactory.newTransformer(transformModel);
        if (transformer.getFrom() == null) {
            transformer = new TransformerWrapper(transformer, transformModel);
        }
        _deployment.getDomain().getTransformerRegistry().removeTransformer(transformer);

        return transformer;
    }

    /**
     * Get the "active" {@link TestMixIn} instance of the specified type.
     * <p/>
     * This method can only be called from inside a test method.
     *
     * @param type The {@link TestMixIn} type, as specified in the {@link TestMixIns} annotation.
     * @param <T> type {@link TestMixIn} type.
     * @return The {@link TestMixIn} instance.
     */
    public <T extends TestMixIn> T getMixIn(Class<T> type) {
        if (_testMixIns == null || _testMixIns.isEmpty()) {
            Assert.fail("No TestMixIns specified on Test class instance.  Use the @TestMixIns annotation.");
        }
        if (_testMixIns.size() != _testMixInInstances.size()) {
            Assert.fail("TestMixIn instances only available during test method execution.");
        }

        int indexOf = _testMixIns.indexOf(type);
        if (indexOf == -1) {
            Assert.fail("Required TestMixIn '" + type.getName() + "' is not specified on TestCase '" + getClass().getName() + "'.");
        }

        return type.cast(_testMixInInstances.get(indexOf));
    }

    /**
     * Finds a resource with a given name.
     * <p/>
     * Searches relative to the implementing class definition.
     *
     * @param name Name of the desired resource
     * @return A {@link java.io.InputStream} object or <tt>null</tt> if no resource with this name is found.
     *
     * @see Class#getResourceAsStream(String)
     */
    public InputStream getResourceAsStream(String name) {
        return getClass().getResourceAsStream(name);
    }

    /**
     * Read a classpath resource and return as a byte array.
     * @param path The path to the classpath resource.  The specified path can be
     * relative to the test class' location on the classpath.
     * @return The resource as an array of bytes.
     */
    public byte[] readResourceBytes(String path) {
        if (path == null) {
            Assert.fail("Resource 'path' not specified.");
        }

        InputStream resourceStream = getResourceAsStream(path);
        if (resourceStream == null) {
            Assert.fail("Resource '" + path + "' not found on classpath relative to test class '" + getClass().getName() + "'.  May need to fix the relative path, or make the path absolute.");
        }

        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        try {
            byte[] readBuffer = new byte[128];
            int readCount = 0;

            while ((readCount = resourceStream.read(readBuffer)) != -1) {
                byteOutStream.write(readBuffer, 0, readCount);
            }
        } catch (IOException e) {
            Assert.fail("Unexpected read error reading classpath resource '" + path + "'" + e.getMessage());
        } finally {
            try {
                resourceStream.close();
            } catch (IOException e) {
                Assert.fail("Unexpected exception closing classpath resource '" + path + "'" + e.getMessage());
            }
        }

        return byteOutStream.toByteArray();
    }

    /**
     * Read a classpath resource and return as a String.
     * @param path The path to the classpath resource.  The specified path can be
     * relative to the test class' location on the classpath.
     * @return The resource as a String.
     */
    public String readResourceString(String path) {
        try {
            return new String(readResourceBytes(path), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Assert.fail("Unexpected exception reading classpath resource '" + path + "' as a String.  Perhaps this resource is a binary resource that cannot be encoded as a String." + e.getMessage());
            return null; // Keep the compiler happy.
        }
    }

    /**
     * Read a classpath resource and return as an XML DOM Document.
     *
     * @param path The path to the classpath resource.  The specified path can be
     * relative to the test class' location on the classpath.
     * @return The resource as a Document.
     */
    public Document readResourceDocument(String path) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(getResourceAsStream(path));
        } catch (Exception e) {
            Assert.fail("Unexpected exception reading classpath resource '" + path + "' as a DOM Document." + e.getMessage());
            return null; // Keep the compiler happy.
        }
    }

    /**
     * Load the SwitchYard configuration model specified by the configModel stream.
     * @param configModel The config model stream.
     * @return The SwitchYard config model.
     */
    public SwitchYardModel loadSwitchYardModel(InputStream configModel) {
        return loadConfigModel(configModel, SwitchYardModel.class);
    }

    /**
     * Load the SwitchYard configuration model specified by the configModel stream.
     * @param <M> Model type.
     * @param configModel The config model stream.
     * @param modelType Model type.
     * @return The SwitchYard config model.
     */
    public <M extends Model> M loadConfigModel(InputStream configModel, Class<M> modelType) {
        if (configModel == null) {
            throw new IllegalArgumentException("null 'configModel' arg.");
        }
        try {
            return modelType.cast(new ModelResource().pull(configModel));
        } catch (IOException e) {
            Assert.fail("Unexpected error building " + modelType.getSimpleName() + ": " + e.getMessage());
        } finally {
            try {
                configModel.close();
            } catch (IOException e) {
                Assert.fail("Unexpected error closing " + modelType.getSimpleName() + " stream: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Compare an XML string (e.g. a result) against a classpath resource.
     * @param xml The XML (as a String) to be compared against the XML in the specified
     * classpath resource.
     * @param resourcePath The path to the classpath resource against which the XML is to be
     * compared.  The specified path can be relative to the test class' location on the classpath.
     */
    public void compareXMLToResource(String xml, String resourcePath) {
        XMLUnit.setIgnoreWhitespace(true);
        try {
            XMLAssert.assertXMLEqual(readResourceString(resourcePath), xml);
        } catch (Exception e) {
            Assert.fail("Unexpected error performing XML comparison: " + e.getMessage());
        }
    }

    /**
     * Compare an XML String (e.g. a result) against another String.
     * @param xml The XML (as a String) to be compared against the XML in the specified
     * classpath resource.
     * @param string The String against which the XML is to be
     * compared.
     */
    public void compareXMLToString(String xml, String string) {
        XMLUnit.setIgnoreWhitespace(true);
        try {
            XMLAssert.assertXMLEqual(string, xml);
        } catch (Exception e) {
            Assert.fail("Unexpected error performing XML comparison.");
        }
    }

    private void mixInSetup() {
        for (TestMixIn mixIn : _testMixInInstances) {
            mixIn.setUp();
        }
    }

    private void mixInBefore() {
        for (TestMixIn mixIn : _testMixInInstances) {
            mixIn.before(_deployment);
        }
    }

    private void mixInAfter() {
        // Apply after MixIns in reverse order...
        for (int i = _testMixInInstances.size() - 1; i >= 0; i--) {
            _testMixInInstances.get(i).after(_deployment);
        }
    }

    private void mixInTearDown() {
        // TearDown MixIns in reverse order...
        for (int i = _testMixInInstances.size() - 1; i >= 0; i--) {
            _testMixInInstances.get(i).tearDown();
        }
    }

    private void createMixInInstances() {
        _testMixInInstances.clear();

        if (_testMixIns == null || _testMixIns.isEmpty()) {
            // No Mix-Ins...
            return;
        }

        for (Class<? extends TestMixIn> mixInType : _testMixIns) {
            try {
                TestMixIn testMixIn = mixInType.newInstance();
                testMixIn.setTestCase(this);
                _testMixInInstances.add(testMixIn);
            } catch (Exception e) {
                e.printStackTrace();
                Assert.fail("Failed to create instance of TestMixIn type " + mixInType.getName() + ".  Make sure it defines a public default constructor.");
            }
        }
    }

    private SwitchYardModel createSwitchYardModel(InputStream configModel, List<Scanner<V1SwitchYardModel>> scanners) {
        Assert.assertNotNull("Test 'configModel' is null.", configModel);

        try {
            SwitchYardModel model = loadSwitchYardModel(configModel);
            ClassLoader classLoader = getClass().getClassLoader();

            if (scanners != null && !scanners.isEmpty() && classLoader instanceof URLClassLoader) {
                MergeScanner<V1SwitchYardModel> merge_scanner = new MergeScanner<V1SwitchYardModel>(V1SwitchYardModel.class, true, scanners);
                List<URL> scanURLs = getScanURLs((URLClassLoader)classLoader);

                ScannerInput<V1SwitchYardModel> scanner_input = new ScannerInput<V1SwitchYardModel>().setName("").setURLs(scanURLs);
                V1SwitchYardModel scannedModel = merge_scanner.scan(scanner_input).getModel();

                return Models.merge(scannedModel, model, false);
            } else {
                return model;
            }
        } catch (java.io.IOException ioEx) {
            throw new RuntimeException("Failed to read switchyard config.", ioEx);
        }
    }

    private void assertDeployed() {
        if (_deployment == null) {
            Assert.fail("TestCase deployment not yet deployed.  You may need to make an explicit call to the deploy() method.");
        }
    }

    private List<Scanner<V1SwitchYardModel>> createScanners(SwitchYardDeploymentConfig deploymentConfig) {
        List<Scanner<V1SwitchYardModel>> scanners = new ArrayList<Scanner<V1SwitchYardModel>>();

        if (deploymentConfig != null) {
            SwitchYardDeploymentScanners scannersAnno = getClass().getAnnotation(SwitchYardDeploymentScanners.class);
            Class<? extends Scanner>[] scannerClasses = null;

            if (scannersAnno != null) {
                scannerClasses = scannersAnno.value();
            }

            if (scannerClasses != null) {
                for (Class<? extends Scanner> scannerClass : scannerClasses) {
                    try {
                        scanners.add(scannerClass.newInstance());
                    } catch (Exception e) {
                        Assert.fail("Exception creating instance of Scanner class '" + scannerClass.getName() + "': " + e.getMessage());
                    }
                }
            } else {
                // Add the default scanners...
                addScanner("org.switchyard.component.bean.config.model.BeanSwitchYardScanner", scanners);
                addScanner("org.switchyard.transform.config.model.TransformSwitchYardScanner", scanners);
            }
        }

        return scanners;
    }

    private List<URL> getScanURLs(URLClassLoader classLoader) {
        URL[] classPathURLs = classLoader.getURLs();
        List<URL> scanURLs = new ArrayList<URL>();

        // Only scan directories.  Above all, we want to make sure we don't
        // start scanning JDK jars etc...
        for (URL classpathURL : classPathURLs) {
            try {
                File file = ClasspathScanner.toClassPathFile(classpathURL);
                if (file.isDirectory()) {
                    scanURLs.add(classpathURL);
                }
            } catch (IOException e) {
                Assert.fail("Failed to convert classpath URL '" + classpathURL + "' to a File instance.");
            }
        }

        return scanURLs;
    }

    private void addScanner(String className, List<Scanner<V1SwitchYardModel>> scanners) {
        try {
            scanners.add((Scanner) Class.forName(className).newInstance());
        } catch (Exception e) {
            // Ignore...
            return;
        }
    }

    private final class TransformerWrapper extends BaseTransformer {

        private Transformer _transformer;
        private TransformModel _transformModel;

        private TransformerWrapper(Transformer transformer, TransformModel transformModel) {
            this._transformer = transformer;
            this._transformModel = transformModel;
        }

        @Override
        public Object transform(Object from) {
            return _transformer.transform(from);
        }

        @Override
        public QName getFrom() {
            return _transformModel.getFrom();
        }

        @Override
        public QName getTo() {
            return _transformModel.getTo();
        }
    }
}
