package com.tungsten.fclcore.mod.modinfo

import kotlinx.serialization.json.Json

/**
 * 元数据解析统一配置：忽略未知键，非法值归并为默认值（缺失字段用构造器默认值兜底）。
 *
 * isLenient：野生 mod 的 mcmod.info / mods.toml 元数据经常不严格（数字未加引号写在
 * String 字段里等）。原 Gson 实现默认宽松、能读出来，严格模式会直接抛异常让整个
 * 元数据解析失败、模组退化成只有文件名，因此这里保持同等宽松度。
 */
val MOD_METADATA_JSON: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}
