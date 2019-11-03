package com.licslan.licslan.po;

import lombok.Data;
import org.jeecgframework.poi.excel.annotation.Excel;

@Data
public class User {

    private Long id;
    @Excel(name = "姓名",isImportField = "name",orderNum = "0")
    private String name;
    @Excel(name = "年龄",isImportField = "age",orderNum = "1")
    private Long age;
}
