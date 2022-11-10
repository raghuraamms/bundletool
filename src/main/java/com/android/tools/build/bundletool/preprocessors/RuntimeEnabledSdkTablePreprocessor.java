/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.tools.build.bundletool.preprocessors;

import static com.android.tools.build.bundletool.sdkmodule.DexAndResourceRepackager.getCompatSdkConfigPathInAssets;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Preprocessor that generates RuntimeEnabledSdkTable.xml config for app bundles that have
 * runtime-enabled SDK dependencies.
 *
 * <p>RuntimeEnabledSdkTable.xml contains paths to compat SDK config files inside the assets
 * directory. There is 1 compat SDK config file per runtime-enabled SDK dependency of the app. Here
 * is what example RuntimeEnabledSdkTable.xml looks like:
 *
 * <pre>{@code
 * <runtime-enabled-sdk-table>
 *   <runtime-enabled-sdk>
 *     <package-name>com.sdk1</package-name>
 *     <compat-config-path>RuntimeEnabledSdk-com.sdk1/CompatSdkConfig.xml</compat-config-path>
 *   </runtime-enabled-sdk>
 *   <runtime-enabled-sdk>
 *     <package-name>com.sdk2</package-name>
 *     <compat-config-path>RuntimeEnabledSdk-com.sdk2/CompatSdkConfig.xml</compat-config-path>
 *   </runtime-enabled-sdk>
 * </runtime-enabled-sdk-table>
 * }</pre>
 */
public class RuntimeEnabledSdkTablePreprocessor implements AppBundlePreprocessor {

  private static final String RUNTIME_ENABLED_SDK_TABLE_FILE_PATH =
      "assets/RuntimeEnabledSdkTable.xml";
  private static final String RUNTIME_ENABLED_SDK_TABLE_ELEMENT_NAME = "runtime-enabled-sdk-table";
  private static final String RUNTIME_ENABELD_SDK_ELEMENT_NAME = "runtime-enabled-sdk";
  private static final String SDK_PACKAGE_NAME_ELEMENT_NAME = "package-name";
  private static final String COMPAT_CONFIG_PATH_ELEMENT_NAME = "compat-config-path";

  @Inject
  RuntimeEnabledSdkTablePreprocessor() {}

  @Override
  public AppBundle preprocess(AppBundle bundle) {
    if (bundle.getRuntimeEnabledSdkDependencies().isEmpty()) {
      return bundle;
    }
    BundleModule modifiedBaseModule =
        bundle.getBaseModule().toBuilder()
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create(RUNTIME_ENABLED_SDK_TABLE_FILE_PATH))
                    .setContent(
                        ByteSource.wrap(
                            XmlUtils.documentToString(
                                    getRuntimeEnabledSdkTable(
                                        bundle.getRuntimeEnabledSdkDependencies().keySet()))
                                .getBytes(UTF_8)))
                    .build())
            .build();
    return bundle.toBuilder()
        .setRawModules(
            bundle.getFeatureModules().values().stream()
                .filter(module -> !module.isBaseModule())
                .collect(toImmutableList()))
        .addRawModule(modifiedBaseModule)
        .build();
  }

  private Document getRuntimeEnabledSdkTable(ImmutableSet<String> sdkPackageNames) {
    Document runtimeEnabledSdkTable;
    try {
      runtimeEnabledSdkTable =
          DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
    runtimeEnabledSdkTable.appendChild(
        createRuntimeEnabledSdkTableNode(runtimeEnabledSdkTable, sdkPackageNames));
    return runtimeEnabledSdkTable;
  }

  private Node createRuntimeEnabledSdkTableNode(
      Document xmlFactory, ImmutableSet<String> sdkPackageNames) {
    Element runtimeEnabledSdkTableNode =
        xmlFactory.createElement(RUNTIME_ENABLED_SDK_TABLE_ELEMENT_NAME);
    sdkPackageNames.forEach(
        sdkPackageName ->
            runtimeEnabledSdkTableNode.appendChild(
                createRuntimeEnabledSdkNode(xmlFactory, sdkPackageName)));
    return runtimeEnabledSdkTableNode;
  }

  private Node createRuntimeEnabledSdkNode(Document xmlFactory, String sdkPackageName) {
    Element runtimeEnabledSdkNode = xmlFactory.createElement(RUNTIME_ENABELD_SDK_ELEMENT_NAME);
    Element sdkPackageNameNode = xmlFactory.createElement(SDK_PACKAGE_NAME_ELEMENT_NAME);
    sdkPackageNameNode.setTextContent(sdkPackageName);
    Element compatConfigPathNode = xmlFactory.createElement(COMPAT_CONFIG_PATH_ELEMENT_NAME);
    compatConfigPathNode.setTextContent(getCompatSdkConfigPathInAssets(sdkPackageName));
    runtimeEnabledSdkNode.appendChild(sdkPackageNameNode);
    runtimeEnabledSdkNode.appendChild(compatConfigPathNode);
    return runtimeEnabledSdkNode;
  }
}
