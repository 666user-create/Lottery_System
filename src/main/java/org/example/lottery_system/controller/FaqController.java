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

    /**
     * 新增一条 FAQ,自动生成向量
     * POST /api/faq/add  body: {"question":"怎么参与抽奖","answer":"登录后进入活动页...","category":"抽奖规则"}
     * 修复 C-6：加 @Validated，使 question/answer 非空与长度限制生效（原实现空值/超长直穿 DB → 500）
     */
    @PostMapping("/add")
    public CommonResult<Long> add(@Validated @RequestBody FaqDO faqDO) {
        faqMapper.insert(faqDO);
        // 给问题生成向量;如果向量接口挂了,FAQ 照样能存(用兜底检索),所以 try-catch 包住
        try {
            double[] vector = embeddingClient.embed(faqDO.getQuestion());
            FaqEmbeddingDO embeddingDO = new FaqEmbeddingDO();
            embeddingDO.setFaqId(faqDO.getId());
            embeddingDO.setEmbedding(MAPPER.writeValueAsString(vector));
            faqEmbeddingMapper.insert(embeddingDO);
            log.info("FAQ[{}] 向量生成成功", faqDO.getId());
        } catch (Exception e) {
            log.error("FAQ[{}] 向量生成失败,先走关键词兜底: {}", faqDO.getId(), e.getMessage());
        }
        return CommonResult.success(faqDO.getId());
    }

    /** 删除 FAQ(连同向量) DELETE /api/faq/delete?id=1 */
    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        faqMapper.deleteById(id);
        faqEmbeddingMapper.deleteByFaqId(id);
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
