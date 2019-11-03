package com.licslan.licslan.dao;

import com.licslan.licslan.po.User;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface ImportDao {
    int save(List<User> list);
}
