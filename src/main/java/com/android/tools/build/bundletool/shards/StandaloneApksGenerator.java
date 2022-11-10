/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.shards;

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.standaloneApkVariantTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.mergers.ModuleSplitsToShardMerger;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.SourceStamp.StampType;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.splitters.BinaryArtProfilesInjector;
import com.android.tools.build.bundletool.splitters.CodeTransparencyInjector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Generates standalone APKs sharded by required dimensions. */
public class StandaloneApksGenerator {

  private final Optional<SourceStamp> stampSource;
  private final ModuleSplitterForShards moduleSplitter;
  private final Sharder sharder;
  private final ModuleSplitsToShardMerger shardsMerger;
  private final CodeTransparencyInjector codeTransparencyInjector;
  private final BinaryArtProfilesInjector binaryArtProfilesInjector;

  @Inject
  public StandaloneApksGenerator(
      Optional<SourceStamp> stampSource,
      ModuleSplitterForShards moduleSplitter,
      Sharder sharder,
      ModuleSplitsToShardMerger shardsMerger,
      AppBundle appBundle) {
    this.stampSource = stampSource;
    this.moduleSplitter = moduleSplitter;
    this.sharder = sharder;
    this.shardsMerger = shardsMerger;
    this.codeTransparencyInjector = new CodeTransparencyInjector(appBundle);
    binaryArtProfilesInjector = new BinaryArtProfilesInjector(appBundle);
  }

  /**
   * Generates sharded APKs from the input modules.
   *
   * <p>Each shard targets a specific point in the "ABI" x "Screen Density" configuration space. To
   * generate shards from bundle modules, we generate module splits from each of the given modules
   * and then partition the splits by their targeting into groups of:
   *
   * <ol>
   *   <li>Master splits (have no ABI or density targeting)
   *   <li>ABI splits
   *   <li>Screen density splits
   * </ol>
   *
   * <p>A concrete sharded APK for configuration ("abi=X", "density=Y") is generated by fusing:
   *
   * <ul>
   *   <li>All master splits - these are unconditionally contained within each sharded APK
   *   <li>ABI splits whose targeting is "abi=X"
   *   <li>Density splits whose targeting is "density=Y"
   * </ul>
   */
  public ImmutableList<ModuleSplit> generateStandaloneApks(
      ImmutableList<BundleModule> modules, ApkOptimizations apkOptimizations) {
    // Generate a flat list of splits from all input modules.
    ImmutableList<ModuleSplit> splits =
        modules.stream()
            .flatMap(
                module ->
                    moduleSplitter
                        .generateSplits(module, apkOptimizations.getStandaloneDimensions())
                        .stream())
            .collect(toImmutableList());

    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexCache = Maps.newHashMap();
    return sharder.groupSplitsToShards(splits).stream()
        .map(unfusedShard -> shardsMerger.mergeSingleShard(unfusedShard, dexCache))
        .map(StandaloneApksGenerator::setVariantTargetingAndSplitType)
        .map(this::writeSourceStampInManifest)
        .map(codeTransparencyInjector::inject)
        .map(binaryArtProfilesInjector::inject)
        .collect(toImmutableList());
  }

  /** Sets the variant targeting and split type to standalone. */
  public static ModuleSplit setVariantTargetingAndSplitType(ModuleSplit shard) {
    return shard.toBuilder()
        .setVariantTargeting(standaloneApkVariantTargeting(shard))
        .setSplitType(SplitType.STANDALONE)
        .build();
  }

  private ModuleSplit writeSourceStampInManifest(ModuleSplit shard) {
    return stampSource
        .map(
            stampSource ->
                shard.writeSourceStampInManifest(
                    stampSource.getSource(), StampType.STAMP_TYPE_STANDALONE_APK))
        .orElse(shard);
  }
}
