package org.boozsoft.rest;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.ApiOperation;
import org.boozsoft.domain.entity.FaAddCutMode;
import org.boozsoft.domain.entity.group.GroupFaAddCutMode;
import org.boozsoft.domain.vo.group.GroupExpenditureClassVo;
import org.boozsoft.repo.FaAddCutModeRepository;
import org.boozsoft.repo.group.GroupFaAddCutModeRepository;
import org.boozsoft.util.TreeUtils;
import org.boozsoft.utils.CollectOfUtils;
import org.springbooz.core.tool.result.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sys/faAddCutMode")
public class FaAddCutModeController {

    @Autowired
    private FaAddCutModeRepository groupExpenditureClassRepository;


    @GetMapping("treeDept")
    public Mono<Map> treeDept(String id, String flag) {
        if (StrUtil.isNotBlank(id) && !id.equals("0")) {
            return groupExpenditureClassRepository.findByIdOrderByDeptCode(id)
                    .collectList()
//                    .map(list->TreeUtils.castTreeList(list,GroupExpenditureClassVo.class))
                    .map(list -> {
                        if (StrUtil.isNotBlank(flag)) {
                            return list.stream().filter(item -> flag.contains(item.getFlag())).collect(Collectors.toList());
                        }
                        return list;
                    })
                    .map(list -> CollectOfUtils.mapof(
                            "code", 0,
                            "message", "ok",
                            "type", "success",
                            "result", list
                    ));
        } else {
            return groupExpenditureClassRepository.findAllByOrderByEcCode()
                    .collectList()
                    .map(list -> TreeUtils.castTreeList(list, GroupExpenditureClassVo.class))
                    .map(list -> {
                        if (StrUtil.isNotBlank(flag)) {
                            return list.stream().filter(item -> flag.contains(item.getFlag())).collect(Collectors.toList());
                        }
                        return list;
                    })
                    .map(list -> CollectOfUtils.mapof(
                            "code", 0,
                            "message", "ok",
                            "type", "success",
                            "result", list
                    ));
        }
    }

    @GetMapping("treeDeptByFlag")
    public Mono<Map> treeDeptByFlag() {
        return groupExpenditureClassRepository.findByFlagOrderByEcCode("1")
                .collectList()
                .map(list -> TreeUtils.castTreeList(list, GroupExpenditureClassVo.class))
                .map(list -> CollectOfUtils.mapof(
                        "code", 0,
                        "message", "ok",
                        "type", "success",
                        "result", list
                ));
    }

    @GetMapping("treeDeptByIsEnd")
    public Mono<Map> treeDeptByIsEnd() {
        return groupExpenditureClassRepository.findByFlagAndBendOrderByEcCode("1", "1")
                .collectList()
                .map(list -> TreeUtils.castTreeList(list, GroupExpenditureClassVo.class))
                .map(list -> CollectOfUtils.mapof(
                        "code", 0,
                        "message", "ok",
                        "type", "success",
                        "result", list
                ));
    }


    @GetMapping("findAll")
    @ApiOperation(value = "????????????", notes = "??????code")
    public Mono<R> findAll(Pageable pageable) {
        return groupExpenditureClassRepository.findAllByOrderByEcCode().collectList().map(R::page);
    }

    @GetMapping("all")
    @ApiOperation(value = "????????????", notes = "??????code")
    public Mono<R> findAllDept() {
        return groupExpenditureClassRepository.findAllDeptCodeOrDeptNameByFlag().collectList().map(R::ok);
    }

    @GetMapping("allMinus")
    @ApiOperation(value = "??????????????????????????????", notes = "??????code")
    public Mono<R> allMinus() {
        return groupExpenditureClassRepository.findAllByFlagAndBendAndIsAccrualOrderByEcCodeAsc("1", "1", "2").collectList().map(R::ok);
    }

    @GetMapping("findById")
    @ApiOperation(value = "??????", notes = "??????code")
    public Mono findById(String id) {
        Mono mono = groupExpenditureClassRepository.findById(id);
        return mono;
    }

    @PostMapping("save")
    @ApiOperation(value = "???????????????", notes = "??????code")
    public Mono save(@RequestBody FaAddCutMode object) {
        if (object.getUniqueCode() == null || object.getUniqueCode().equals("")) {
            object.setUniqueCode(IdUtil.objectId());
        }
        if (object.getFlag() == null || object.getFlag().equals("")) {
            object.setFlag("1");
        }

        //1??? ?????????
        if (object.getParentId() == null || object.getParentId().equals("")) {
            object.setParentId("0");
            object.setBend("1");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (object.getCreateDate() == null || object.getCreateDate().equals("")) {
            object.setCreateDate(sdf.format(new Date()));
        }

        //????????????????????????
        if (object.getParentId() != null && !object.getParentId().equals("") && object.getBend().equals("0")) {
            object.setBend("1");
        }
        //?????????????????????
        Mono<FaAddCutMode> sysDeptClassMono = null;
        if (object.getParentId() != null && !object.getParentId().equals("") && !object.getParentId().equals("0") && object.getBend().equals("1")) {
            object.setBend("1");
            sysDeptClassMono = groupExpenditureClassRepository.findById(object.getParentId())
                    .map(v -> {
                        v.setBend("0");
                        return v;
                    })
                    .flatMap(groupExpenditureClassRepository::save);
        }
        //??????
        if (object.getId() == null || object.getId().equals("")) {
            return Objects.isNull(sysDeptClassMono) ? groupExpenditureClassRepository.save(object)
                    .map(o -> R.ok().setResult(o)) : sysDeptClassMono.zipWith(groupExpenditureClassRepository.save(object)
                    .map(o -> R.ok().setResult(o))).thenReturn(R.ok());
        }

        return Objects.isNull(sysDeptClassMono) ? groupExpenditureClassRepository.save(object)
                .map(o -> R.ok().setResult(o)) : sysDeptClassMono.zipWith(groupExpenditureClassRepository.save(object)
                .map(o -> R.ok().setResult(o))).thenReturn(R.ok());
    }

    @DeleteMapping("del")
    @ApiOperation(value = "??????", notes = "??????code")
    public Mono delete(@RequestBody FaAddCutMode object) {
        return groupExpenditureClassRepository.findByIdOrderByDeptCode(object.getId())
                .collectList()
                .flatMap(item -> groupExpenditureClassRepository.deleteAll(item))
                .then(Mono.just(R.ok()));
    }

    @PostMapping("editFlag")
    @ApiOperation(value = "??????????????????", notes = "??????code")
    public Mono editFlag(@RequestBody FaAddCutMode object) {
        if (object.getFlag().equals("1")) {
            object.setFlag("0");
        } else {
            object.setFlag("1");
        }
        return groupExpenditureClassRepository.save(object)
                .map(o -> R.ok().setResult(o));
    }

    @PostMapping("excel")
    @ApiOperation(value = "????????????excel,????????????????????????", notes = "??????code")
    @Transactional
    public Mono excel(@RequestBody List<FaAddCutMode> object) {
        object.stream().forEach(v -> {
            v.setUniqueCode(IdUtil.objectId());
        });
        return groupExpenditureClassRepository.saveAll(object)
                .collectList()
                .map(o -> R.ok().setResult(o));
    }

    @GetMapping("/getMaxCode")
    @ApiOperation(value = "????????????code", notes = "????????????code")
    public Mono getMaxCode(String id) {
        return groupExpenditureClassRepository.findMaxCodeByPid(id)
                .map(v -> {
                    return (Integer.valueOf(v) + 1) + "";
                })
                .defaultIfEmpty("0")
                .map(v -> R.ok().setResult(v));
    }

    @GetMapping("/getTotalData")
    @ApiOperation(value = "???????????????????????????", notes = "???????????????????????????")
    public Mono getTotalData(){
        return groupExpenditureClassRepository.findAll()
                .collectList()
                .map(v-> v.size())
                .map(v-> R.ok().setResult(v));
    }

}
