/*
rebuild - Building your business-systems freely.
Copyright (C) 2019 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server.configuration.portals;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.server.Application;
import com.rebuild.server.configuration.ConfigEntry;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.metadata.MetadataHelper;
import com.rebuild.server.metadata.entityhub.DisplayType;
import com.rebuild.server.metadata.entityhub.EasyMeta;
import com.rebuild.server.service.bizz.privileges.User;

import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.dialect.FieldType;
import cn.devezhao.persist4j.engine.ID;

/**
 * 表单构造
 * 
 * @author devezhao zhaofang123@gmail.com
 * @since 2019/06/03
 */
public class FormsBuilder extends FormsManager {
	
	public static final FormsBuilder instance = new FormsBuilder();
	private FormsBuilder() { }
	
	// 分割线
	private static final String DIVIDER_LINE = "$DIVIDER$";

	/**
	 * 表单-新建
	 * 
	 * @param entity
	 * @param user
	 * @return
	 */
	public JSON buildForm(String entity, ID user) {
		return buildForm(entity, user, null);
	}
	
	/**
	 * 表单-编辑
	 * 
	 * @param entity
	 * @param user
	 * @param record
	 * @return
	 */
	public JSON buildForm(String entity, ID user, ID record) {
		return buildModel(entity, user, record, false);
	}
	
	/**
	 * 视图
	 * 
	 * @param entity
	 * @param user
	 * @param record
	 * @return
	 */
	public JSON buildView(String entity, ID user, ID record) {
		Assert.notNull(record, "[record] not be null");
		return buildModel(entity, user, record, true);
	}
	
	/**
	 * @param entity
	 * @param user
	 * @param record
	 * @param isView 视图模式?
	 * @return
	 */
	protected JSON buildModel(String entity, ID user, ID record, boolean isView) {
		Assert.notNull(entity, "[entity] not be null");
		Assert.notNull(user, "[user] not be null");
		
		final Entity entityMeta = MetadataHelper.getEntity(entity);
		final User currentUser = Application.getUserStore().getUser(user);
		final Date now = CalendarUtils.now();
		
		// 判断表单权限
		if (record == null) {
			if (entityMeta.getMasterEntity() != null) {
				ID masterId = MASTERID4NEWSLAVE.get();
				Assert.notNull(masterId, "Call #setCurrentMasterId first");
				MASTERID4NEWSLAVE.set(null);
				
				if (!Application.getSecurityManager().allowedU(user, masterId)) {
					return formatModelError("你没有权限向此记录添加明细");
				}
			} else if (!Application.getSecurityManager().allowedC(user, entityMeta.getEntityCode())) {
				return formatModelError("没有新建权限");
			}
			
		} else {
			if (isView) {
				if (!Application.getSecurityManager().allowedR(user, record)) {
					return formatModelError("你没有读取此记录的权限");
				}
			} else {
				if (!Application.getSecurityManager().allowedU(user, record)) {
					return formatModelError("你没有编辑此记录的权限");
				}
			}
		}
		
		ConfigEntry model = getFormLayout(entity, user);
		JSONArray elements = (JSONArray) model.getJSON("elements");
		if (elements == null || elements.isEmpty()) {
			return formatModelError("此表单布局尚未配置，请配置后使用");
		}
		
		Record data = null;
		if (!elements.isEmpty() && record != null) {
			data = findRecord(record, user, elements);
			if (data == null) {
				return formatModelError("此记录已被删除，或你对此记录没有读取权限");
			}
		}
		
		// Check and clean
		for (Iterator<Object> iter = elements.iterator(); iter.hasNext(); ) {
			JSONObject el = (JSONObject) iter.next();
			String fieldName = el.getString("field");
			
			// 分割线
			if (fieldName.equalsIgnoreCase(DIVIDER_LINE)) {
				if (!isView) {  // 表单页暂不支持
					iter.remove();
				}
				continue;
			}
			// 已删除字段
			if (!entityMeta.containsField(fieldName)) {
				LOG.warn("Unknow field '" + fieldName + "' in '" + entity + "'");
				iter.remove();
				continue;
			}
			
			Field fieldMeta = entityMeta.getField(fieldName);
			EasyMeta easyField = new EasyMeta(fieldMeta);
			el.put("label", easyField.getLabel());
			final DisplayType dt = easyField.getDisplayType();
			el.put("type", dt.name());
			el.put("nullable", fieldMeta.isNullable());
			el.put("readonly", false);
			
			// 不可更新字段
			if (data != null && !fieldMeta.isUpdatable()) {
				el.put("readonly", true);
			}
			
			// 针对字段的配置
			
			JSONObject fieldExt = easyField.getFieldExtConfig();
			for (Map.Entry<String, Object> e : fieldExt.entrySet()) {
				el.put(e.getKey(), e.getValue());
			}
			
			// 不同类型的处理
			
			int dateLength = -1;
			
			if (dt == DisplayType.PICKLIST) {
				JSONArray options = PickListManager.instance.getPickList(fieldMeta);
				el.put("options", options);
			}
			else if (dt == DisplayType.DATETIME) {
				if (!el.containsKey("datetimeFormat")) {
					el.put("datetimeFormat", DisplayType.DATETIME.getDefaultFormat());
				}
				dateLength = el.getString("datetimeFormat").length();
			}
			else if (dt == DisplayType.DATE) {
				if (!el.containsKey("dateFormat")) {
					el.put("dateFormat", DisplayType.DATE.getDefaultFormat());
				}
				dateLength = el.getString("dateFormat").length();
			}
			else if (dt == DisplayType.CLASSIFICATION) {
				el.put("openLevel", ClassificationManager.instance.getOpenLevel(fieldMeta));
			}
			
			// 编辑/视图
			if (data != null) {
				Object value = wrapFieldValue(data, easyField, isView);
				if (value != null) {
					if (dt == DisplayType.BOOL && !isView) {
						value = "是".equals(value) ? "T" : "F";
					}
					el.put("value", value);
				}
			}
			// 新建记录
			else {
				if (!fieldMeta.isCreatable()) {
					el.put("readonly", true);
					if (fieldName.equals(EntityHelper.CreatedOn) || fieldName.equals(EntityHelper.ModifiedOn)) {
						el.put("value", CalendarUtils.getUTCDateTimeFormat().format(now));
					} else if (fieldName.equals(EntityHelper.CreatedBy) || fieldName.equals(EntityHelper.ModifiedBy) || fieldName.equals(EntityHelper.OwningUser)) {
						el.put("value", new Object[] { currentUser.getId(), currentUser.getFullName(), "User" });
					} else if (fieldName.equals(EntityHelper.OwningDept)) {
						el.put("value", new Object[] { currentUser.getOwningDept().getIdentity(), currentUser.getOwningDept().getName(), "Department" });
					}
				}
				
				if (dt == DisplayType.PICKLIST) {
					JSONArray options = el.getJSONArray("options");
					for (Object o : options) {
						JSONObject item = (JSONObject) o;
						if (item.getBooleanValue("default")) {
							el.put("value", item.getString("id"));
							break;
						}
					}
				} else if (dt == DisplayType.SERIES) {
					el.put("value", "自动值 (保存后显示)");
				} else {
					Object dv = FormDefaultValue.exprDefaultValue(fieldMeta);
					if (dv != null) {
						if (dateLength > -1) {
							dv = dv.toString().substring(0, dateLength);
						}
						el.put("value", dv);
					}
				}
			}
		}
		
		if (elements.isEmpty()) {
			return formatModelError("此表单布局尚未配置，请配置后使用");
		}
		
		// 主/明细实体处理
		if (entityMeta.getMasterEntity() != null) {
			model.set("isSlave", true);
		} else if (entityMeta.getSlaveEntity() != null) {
			model.set("isMaster", true);
			model.set("slaveMeta", EasyMeta.getEntityShow(entityMeta.getSlaveEntity()));
		}
		
		if (data != null && data.hasValue(EntityHelper.ModifiedOn)) {
			model.set("lastModified", data.getDate(EntityHelper.ModifiedOn).getTime());
		}
		
		model.set("id", null);  // form's ID of config
		return model.toJSON();
	}
	
	/**
	 * @param error
	 * @return
	 */
	private JSONObject formatModelError(String error) {
		JSONObject cfg = new JSONObject();
		cfg.put("error", error);
		return cfg;
	}
	
	/**
	 * @param id
	 * @param user
	 * @param elements
	 * @return
	 */
	private Record findRecord(ID id, ID user, JSONArray elements) {
		if (elements.isEmpty()) {
			return null;
		}
		
		Entity entity = MetadataHelper.getEntity(id.getEntityCode());
		StringBuffer ajql = new StringBuffer("select ");
		for (Object element : elements) {
			JSONObject el = (JSONObject) element;
			String field = el.getString("field");
			if (field.startsWith("$")) {
				continue;
			}
			if (!entity.containsField(field)) {
				continue;
			}
			
			Field fieldMeta = entity.getField(field);
			
			// REFERENCE
			if (fieldMeta.getType() == FieldType.REFERENCE) {
				int ec = fieldMeta.getReferenceEntity().getEntityCode();
				if (!(ec == EntityHelper.ClassificationData || ec == EntityHelper.PickList)) {
					ajql.append('&').append(field).append(',');
				}
			}
			
			ajql.append(field).append(',');
		}
		
		if (entity.containsField(EntityHelper.ModifiedOn)) {
			ajql.append(EntityHelper.ModifiedOn);
		} else {
			ajql.deleteCharAt(ajql.length() - 1);
		}
		
		ajql.append(" from ").append(entity.getName())
				.append(" where ").append(entity.getPrimaryField().getName())
				.append(" = '").append(id).append("'");
		
		return Application.getQueryFactory().createQuery(ajql.toString(), user).record();
	}
	

	/**
	 * 封装表单/布局所用的字段值
	 * 
	 * @param data
	 * @param field
	 * @param isView
	 * @return
	 * 
	 * @see FieldValueWrapper
	 * @see #findRecord(ID, ID, JSONArray)
	 */
	protected static Object wrapFieldValue(Record data, EasyMeta field, boolean isView) {
		String fieldName = field.getName();
		if (data.hasValue(fieldName)) {
			Object value = data.getObjectValue(fieldName);
			DisplayType dt = field.getDisplayType();
			if (dt == DisplayType.PICKLIST) {
				ID pickValue = (ID) value;
				if (isView) {
					return StringUtils.defaultIfBlank(
							PickListManager.instance.getLabel(pickValue), FieldValueWrapper.MISS_REF_PLACE);
				} else {
					return pickValue.toLiteral();
				}
			}
			else if (dt == DisplayType.CLASSIFICATION) {
				ID itemValue = (ID) value;
				String itemName = ClassificationManager.instance.getFullName(itemValue);
				itemName = StringUtils.defaultIfBlank(itemName, FieldValueWrapper.MISS_REF_PLACE);
				return isView ? itemName : new String[] { itemValue.toLiteral(), itemName };
			} 
			else if (value instanceof ID) {
				ID idValue = (ID) value;
				String idLabel = idValue.getLabel();
				if (idLabel == null) {
					idLabel = '[' + idValue.toLiteral().toUpperCase() + ']';
				}
				
				String belongEntity = MetadataHelper.getEntityName(idValue);
				return new String[] { idValue.toLiteral(), idLabel, belongEntity };
			} 
			else {
				Object ret = FieldValueWrapper.wrapFieldValue(value, field);
				// 编辑记录时要去除千分位
				if (!isView && (dt == DisplayType.NUMBER || dt == DisplayType.DECIMAL)) {
					ret = ret.toString().replace(",", "");
				}
				return ret;
			}
		}
		return null;
	}
	
	// -- 主/明细实体权限处理
	
	private static final ThreadLocal<ID> MASTERID4NEWSLAVE = new ThreadLocal<>();
	/**
	 * 创建明细实体必须指定主实体，以便验证权限
	 * 
	 * @param masterId
	 */
	public static void setCurrentMasterId(ID masterId) {
		MASTERID4NEWSLAVE.set(masterId);
	}
}