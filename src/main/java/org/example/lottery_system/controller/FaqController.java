package org.example.lottery_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.dao.dataobject.FaqDO;
import org.example.lottery_system.dao.dataobject.FaqEmbeddingDO;
import org.example.lottery_system.dao.mapper.FaqEmbeddingMapper;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.example.lottery_system.service.ai.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQ 知识库管理接口(仅管理员用)
 * 鉴权说明:管理员身份校验由 LoginInterceptor 统一完成(/api/faq/** 要求 identity=ADMIN)
 * 参数校验:修复 C-6 后由 @Validated + FaqDO 上的 Jakarta Validation 注解保证
 */
@RestController
@RequestMapping("/api/faq")
public class FaqController {

    private static final Logger log = LoggerFactory.getLogger(FaqController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private FaqMapper faqMapper;
    @Autowired private FaqEmbeddingMapper faqEmbeddingMapper;
    @Autowired private EmbeddingClient embeddingClient;
    /** 向量索引服务:FAQ 增删后必须同步索引,否则客服检索不到新知识(修复项) */
    @Autowired private org.example.lottery_system.service.ai.rag.KnowledgeIndexService knowledgeIndexService;

    /**
     * 新增一条 FAQ,自动生成向量
     * POST /api/faq/add  body: {"question":"怎么参与抽奖","answer":"登录后进入活动页...","category":"抽奖规则"}
     * 修复 C-6：加 @Validated，使 question/answer 非空与长度限制生效（原实现空值/超长直穿 DB → 500）
     */
    @PostMapping("/add")
    public CommonResult<Long> add(@Validated @RequestBody FaqDO faqDO) {
        faqMapper.insert(faqDO);
        // faq_embedding 表是 simple 模式的向量存储。
        // 现在向量来源默认是本地模型(不需要 Key、不产生网络报错),所以两种模式下都同步维护,
        // 保证任何时候切到 simple 模式,知识库向量都是齐的。
        if (embeddingClient.isUsable()) {
            try {
                double[] vector = embeddingClient.embed(faqDO.getQuestion());
                FaqEmbeddingDO embeddingDO = new FaqEmbeddingDO();
                embeddingDO.setFaqId(faqDO.getId());
                embeddingDO.setEmbedding(MAPPER.writeValueAsString(vector));
                faqEmbeddingMapper.insert(embeddingDO);
                log.info("FAQ[{}] 向量生成成功({})", faqDO.getId(), embeddingClient.describe());
            } catch (Exception e) {
                log.warn("FAQ[{}] 向量生成失败,simple 模式将走关键词兜底: {}", faqDO.getId(), e.getMessage());
            }
        }
        // 同步更新框架化向量索引(SimpleVectorStore),否则客服检索不到这条新知识
        try {
            knowledgeIndexService.addOrUpdateFaq(faqDO);
        } catch (Exception e) {
            log.error("FAQ[{}] 写入向量索引失败,客服可能暂时检索不到: {}", faqDO.getId(), e.getMessage());
        }
        return CommonResult.success(faqDO.getId());
    }

    /** 删除 FAQ(连同向量) DELETE /api/faq/delete?id=1 */
    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        faqMapper.deleteById(id);
        faqEmbeddingMapper.deleteByFaqId(id);
        // 同步从向量索引移除,否则客服仍会检索到已删除的知识
        try {
            knowledgeIndexService.removeFaqFromIndex(id);
        } catch (Exception e) {
            log.error("FAQ[{}] 从向量索引移除失败: {}", id, e.getMessage());
        }
        return CommonResult.success(true);
    }

    /** 分页查询 GET /api/faq/list?page=1&pageSize=10 */
    @GetMapping("/list")
    public CommonResult<Map<String, Object>> list(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<FaqDO> list = faqMapper.selectPage(offset, pageSize);
        int total = faqMapper.count();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return CommonResult.success(result);
    }
}
