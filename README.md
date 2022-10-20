# AdaSetAndMMA
Source code and dataset for the paper "An Empirical Study on the Adaptations to the Breaking Changes: Collection, Strategies, and Re-usability."

## AdaSet

AdaSet is a series of realistic datasets for the adaptation generation task, which guarantees that each adaptation in AdaSet owns at least one reference example that adapts to the same breaking change.
AdaSet contains three scenarios,

- Original Dataset: Original_Source.zip
- Development Dataset: Development_Source.zip
- Upgrade Dataset: Upgrade_Source.zip

## Adaptation generation

The `Meditor+Merge+API Transform (MMA)` could be executed by default by running the `MeditorMerge.java`.

The `MeditorMerge.java` is the entrance of `Meditor+Merge`, `Meditor+Merge+API Transform`, `Meditor+Merge+Delete`, and `Meditor+Merge+Copy`.

In the `MeditorMerge.java`,
- The `dataset` field indicates the evaluation dataset, whose possible values are Original, Development, and Upgrade.
- The `combination` field indicates the composed approaches if the `Meditor` series approaches matched failed, whose possible values are Original, Development, and Upgrade.
- The `isCombine` field indicates whether opening the combination branch.

The `Meditor+API Transform` could be executed by default by running the `Meditor.java`.

The `MeditorMerge.java` is the entrance of `Meditor`, `Meditor+API Transform`, `Meditor+Delete`, and `Meditor+Copy`, whose parameters config is the same as `MeditorMerge.java`.

The `API Transform` could be executed by default by running the `APITransform.java`.

The `APITransform.java` is the entrance of `API Transform`, `Delete`, and `Copy`, which uses the `combination` field to indicate the running approaches and the `dataset` field to indicate the evaluation dataset.
