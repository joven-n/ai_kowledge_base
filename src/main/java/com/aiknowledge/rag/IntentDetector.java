package com.aiknowledge.rag;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于关键词的意图检测器
 * 用于区分：闲聊问候（直接LLM回答） vs 跨境电商查询（检索知识库后LLM回答）
 */
@Component
public class IntentDetector {

    public enum Intent {
        CHAT,              // 闲聊/问候/通用问题（直接调用 LLM）
        CROSS_BORDER_QUERY // 跨境电商相关问题（先检索知识库，再调用 LLM）
    }

    // 问候语模式（正则）
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(你好|您好|嗨|hi|hello|hey|早上好|晚上好|下午好|再见|拜拜|bye|谢谢|感谢|好的|ok|嗯|哦|哈|呵呵|哈哈|谢谢|多谢|辛苦了|打扰了|在吗|在不在)" +
            "|.*(怎么啦|怎么样|最近如何|好吗|还好吗|吃了吗|睡了没)" +
            "|.*(你是谁|你叫什么|介绍一下你自己|你能做什么|你的功能)" +
            "|.*(讲个笑话|来首歌|聊聊天|无聊|陪我聊聊)",
            Pattern.CASE_INSENSITIVE
    );

    // 跨境电商关键词集合
    private static final Set<String> CROSS_BORDER_KEYWORDS = Stream.of(
        // ========== 平台类 ==========
        "亚马逊", "amazon", "lazada", "shopee", "速卖通", "aliexpress", "ali express",
        "ebay", "wish", "temu", "tiktok", "tiktok shop", "独立站", "shopify", "woocommerce",
        "alibaba", "1688", "敦煌网", "dhgate", "walmart", "target", "best buy",
        "美客多", "mercadolivre", "mercado libre", "ozon", "wildberries",
        "cdiscount", "fnac", "otto", "bol.com", "emag",

        // ========== 物流仓储 ==========
        "fba", "fbm", "海外仓", "保税仓", "第三方仓", "自发货",
        "海运", "空运", "快递", "小包", "专线", "邮政", "ems", "dhl", "ups", "fedex", "tnt", "aramex",
        "头程物流", "尾程派送", "最后一公里", "清关", "报关", "关税", "进口税", "出口税",
        "vat", "增值税", "消费税", "关税起征点", "hs编码", "hs code",
        "集装箱", "货柜", "柜型", "整柜", "拼柜", "lcl", "fcl",
        "物流时效", "配送时间", "妥投率", "丢件率", "退货率",
        "亚马逊仓库", "awd", "sfp", "prime",

        // ========== 运营推广 ==========
        "listing", "详情页", "产品页", "a+页面", "a+ content", "品牌故事",
        "ppc", "广告", "cpc", "cpm", "roas", "acos", "tacos",
        "关键词", "search term", "搜索词", "长尾词", "埋词",
        "seo", "搜索引擎优化", "排名优化", "自然排名", "付费排名",
        "促销", "coupon", "优惠券", "code", "折扣码", "prime day", "黑五", "网一", "会员日",
        "秒杀", "ld", "bd", "dotd", "vine计划", "测评", "review", "feedback",
        "刷单", "刷评", "合并变体", "拆分变体", "父体", "子体",
        "品牌备案", "brand registry", "品牌授权", "商标注册", "专利", "知识产权",

        // ========== 选品数据 ==========
        "选品", "市场调研", "竞品分析", "蓝海", "红海", "利基市场", "niche",
        "bsr", "销量估算", "月销量", "日均销量",
        "利润率", "毛利率", "净利率", "roi", "投入产出比",
        "sku", "asin", "upc", "ean", "gtin", "fnsku",
        "库存管理", "补货计划", "周转率", "滞销", "积压",
        "季节性", "旺季", "淡季", "q1", "q2", "q3", "q4",

        // ========== 工具软件 ==========
        "erp", "卖家精灵", "helium10", "h10", " jungle scout", "js",
        "keepa", "camelcamelcamel", "sellerboard", "profit bandit",
        "店小秘", "芒果店长", "通途", "马帮", "易仓", "数字酋长",
        "chatgpt", "ai选品", "ai写文案", "ai翻译",

        // ========== 政策合规 ==========
        "合规", "侵权", "假冒伪劣", "假货投诉", "ip投诉",
        "账号安全", "关联", "封号", "申诉", "绩效通知",
        "odr", "迟发率", "缺陷率", "退货不满意率", "客服满意度",
        "产品认证", "ce认证", "fcc认证", "ul认证", "fda认证", "rohs", "reach",
        "包装法", "weee", "包装回收", "欧代", "英代",

        // ========== 付款结算 ==========
        "收款", "pingpong", "payoneer", "p卡", "万里汇", "worldfirst", "wf",
        "连连支付", "airwallex", "亚马逊转账", "打款周期",
        "汇率", "结汇", "外汇", "退税", "出口退税",

        // ========== 其他跨境电商术语 ==========
        "跨境", "出海", "外贸", "进出口", "电商", "电子商务",
        "无货源", "一件代发", "dropshipping", "代发模式",
        "铺货模式", "精铺", "精品模式", "垂直品类",
        "供应链", "工厂直供", "oem", "odm", "obm",
        "样品", "moq", "最小起订量", "交期", "lead time"
    ).map(String::toLowerCase).collect(Collectors.toSet());

    /**
     * 检测用户问题意图
     *
     * @param question 用户输入的问题
     * @return 意图类型
     */
    public Intent detect(String question) {
        if (question == null || question.trim().isEmpty()) {
            return Intent.CHAT;
        }

        String lowerQuestion = question.trim().toLowerCase();

        // 1. 先检测是否为问候/闲聊类型
        if (isGreeting(lowerQuestion)) {
            return Intent.CHAT;
        }

        // 2. 检测是否包含跨境电商相关关键词
        if (containsCrossBorderKeywords(lowerQuestion)) {
            return Intent.CROSS_BORDER_QUERY;
        }

        // 3. 默认为普通对话
        return Intent.CHAT;
    }

    /**
     * 判断是否为问候/闲聊类型
     */
    private boolean isGreeting(String text) {
        return GREETING_PATTERN.matcher(text).matches() || GREETING_PATTERN.matcher(text).find();
    }

    /**
     * 判断是否包含跨境电商关键词
     */
    private boolean containsCrossBorderKeywords(String text) {
        // 将文本按空格和中文分词边界拆分
        String[] tokens = text.split("[\\s\\p{Punct}]+");

        for (String token : tokens) {
            if (CROSS_BORDER_KEYWORDS.contains(token)) {
                return true;
            }
        }

        // 同时检查原始文本中是否包含某些需要连续匹配的关键词（如中文词）
        for (String keyword : CROSS_BORDER_KEYWORDS) {
            if (!keyword.matches("[a-z0-9]+") && text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}
