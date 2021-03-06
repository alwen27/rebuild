/*
Copyright (c) REBUILD <https://getrebuild.com/> and its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.server.service.project;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.PersistManagerFactory;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import com.rebuild.server.Application;
import com.rebuild.server.configuration.ConfigEntry;
import com.rebuild.server.configuration.ProjectManager;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.metadata.MetadataHelper;
import com.rebuild.server.service.BaseService;
import com.rebuild.server.service.DataSpecificationException;
import com.rebuild.server.service.ObservableService;
import com.rebuild.server.service.OperatingContext;
import com.rebuild.server.service.base.AttachmentAwareObserver;
import org.springframework.util.Assert;

import java.text.MessageFormat;
import java.util.List;
import java.util.Observer;
import java.util.Set;

/**
 * @author devezhao
 * @since 2020/7/27
 */
public abstract class BaseTaskService extends ObservableService {

    protected BaseTaskService(PersistManagerFactory aPMFactory, List<Observer> observers) {
        super(aPMFactory, observers);
    }

    /**
     * 是否成员，非成员禁止操作
     *
     * @param user
     * @param taskOrProject
     * @return
     */
    protected boolean checkInMembers(ID user, ID taskOrProject) {
        if (user == null) user = Application.getCurrentUser();
        Assert.notNull(taskOrProject, "taskOrProject");

        ConfigEntry c = taskOrProject.getEntityCode() == EntityHelper.ProjectTask
                ? ProjectManager.instance.getProjectByTask(taskOrProject, null)
                : ProjectManager.instance.getProject(taskOrProject, null);
        if (c != null && c.get("members", Set.class).contains(user)) return true;

        throw new DataSpecificationException("非项目成员禁止操作");
    }
}
