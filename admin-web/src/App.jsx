import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  Layout,
  List,
  message,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography
} from "antd";

const { Header, Content } = Layout;
const { Title, Text } = Typography;
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

async function request(path, { token, method = "GET", body } = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(payload?.message || payload?.error || `Request failed: ${response.status}`);
  }
  return payload;
}

export default function App() {
  const [messageApi, contextHolder] = message.useMessage();
  const [token, setToken] = useState("");
  const [adminProfile, setAdminProfile] = useState(null);
  const [users, setUsers] = useState([]);
  const [forms, setForms] = useState([]);
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [loading, setLoading] = useState(false);

  const [loginForm] = Form.useForm();
  const [createForm] = Form.useForm();
  const [permissionsForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  const selectedUser = useMemo(
    () => users.find((item) => item.userId === selectedUserId) || null,
    [users, selectedUserId]
  );

  async function refreshAdminData(currentToken = token) {
    if (!currentToken) return;
    setLoading(true);
    try {
      const [profile, userList, formList] = await Promise.all([
        request("/me", { token: currentToken }),
        request("/admin/users", { token: currentToken }),
        request("/admin/forms", { token: currentToken })
      ]);
      setAdminProfile(profile);
      setUsers(userList);
      setForms(formList);
      if (!selectedUserId && userList.length > 0) {
        setSelectedUserId(userList[0].userId);
      }
    } catch (error) {
      messageApi.error(error.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (selectedUser) {
      permissionsForm.setFieldsValue({
        admin: selectedUser.admin,
        canUpload: selectedUser.canUpload,
        canDeleteCache: selectedUser.canDeleteCache,
        formIds: selectedUser.formIds
      });
      passwordForm.resetFields();
    }
  }, [selectedUser, permissionsForm, passwordForm]);

  async function handleLogin(values) {
    setLoading(true);
    try {
      const payload = await request("/auth/login", {
        method: "POST",
        body: values
      });
      if (!payload.admin) {
        throw new Error("当前账号不是管理员，不能进入后台");
      }
      setToken(payload.token);
      setAdminProfile(payload);
      messageApi.success("管理员登录成功");
      await refreshAdminData(payload.token);
    } catch (error) {
      messageApi.error(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateUser(values) {
    try {
      await request("/admin/users", {
        token,
        method: "POST",
        body: values
      });
      messageApi.success("账号已创建");
      createForm.resetFields();
      refreshAdminData();
    } catch (error) {
      messageApi.error(error.message);
    }
  }

  async function handleUpdatePermissions(values) {
    if (!selectedUser) return;
    try {
      await request(`/admin/users/${selectedUser.userId}/permissions`, {
        token,
        method: "POST",
        body: values
      });
      messageApi.success("权限已更新");
      refreshAdminData();
    } catch (error) {
      messageApi.error(error.message);
    }
  }

  async function handleResetPassword(values) {
    if (!selectedUser) return;
    try {
      await request(`/admin/users/${selectedUser.userId}/reset-password`, {
        token,
        method: "POST",
        body: values
      });
      messageApi.success("密码已重置");
      passwordForm.resetFields();
    } catch (error) {
      messageApi.error(error.message);
    }
  }

  async function handleToggleStatus(user, enabled) {
    try {
      await request(`/admin/users/${user.userId}/status`, {
        token,
        method: "POST",
        body: { enabled }
      });
      messageApi.success("状态已更新");
      refreshAdminData();
    } catch (error) {
      messageApi.error(error.message);
    }
  }

  const userColumns = [
    {
      title: "手机号",
      dataIndex: "phoneNumber"
    },
    {
      title: "姓名",
      dataIndex: "displayName"
    },
    {
      title: "启用状态",
      dataIndex: "enabled",
      render: (enabled, record) => (
        <Switch checked={enabled} onChange={(checked) => handleToggleStatus(record, checked)} />
      )
    },
    {
      title: "权限",
      render: (_, record) => (
        <Space wrap>
          {record.admin ? <Tag color="blue">管理员</Tag> : null}
          {record.canUpload ? <Tag color="green">可上传</Tag> : <Tag>仅采集</Tag>}
          {record.canDeleteCache ? <Tag color="orange">可清缓存</Tag> : null}
        </Space>
      )
    },
    {
      title: "可用表单",
      dataIndex: "formIds",
      render: (value) => value.map((item) => <Tag key={item}>{item}</Tag>)
    }
  ];

  if (!token) {
    return (
      <Layout className="app-shell">
        {contextHolder}
        <Header className="app-header">
          <Title level={3} style={{ color: "white", margin: 0 }}>
            船厂采集后台
          </Title>
        </Header>
        <Content className="app-content">
          <Row justify="center">
            <Col xs={24} md={14} xl={10}>
              <Card>
                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                  <Title level={4}>管理员登录</Title>
                  <Alert
                    type="info"
                    showIcon
                    message="默认管理员账号"
                    description="13900000000 / admin123"
                  />
                  <Form form={loginForm} layout="vertical" onFinish={handleLogin}>
                    <Form.Item
                      label="手机号"
                      name="phoneNumber"
                      rules={[{ required: true, message: "请输入手机号" }]}
                    >
                      <Input placeholder="13900000000" />
                    </Form.Item>
                    <Form.Item
                      label="密码"
                      name="password"
                      rules={[{ required: true, message: "请输入密码" }]}
                    >
                      <Input.Password placeholder="admin123" />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" loading={loading} block>
                      登录后台
                    </Button>
                  </Form>
                </Space>
              </Card>
            </Col>
          </Row>
        </Content>
      </Layout>
    );
  }

  return (
    <Layout className="app-shell">
      {contextHolder}
      <Header className="app-header">
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={3} style={{ color: "white", margin: 0 }}>
              船厂采集后台
            </Title>
          </Col>
          <Col>
            <Space>
              <Text style={{ color: "white" }}>
                {adminProfile?.displayName} / {adminProfile?.phoneNumber}
              </Text>
              <Button
                onClick={() => {
                  setToken("");
                  setAdminProfile(null);
                  setUsers([]);
                  setForms([]);
                  setSelectedUserId(null);
                }}
              >
                退出
              </Button>
            </Space>
          </Col>
        </Row>
      </Header>
      <Content className="app-content">
        <Spin spinning={loading}>
          <Row gutter={[16, 16]}>
            <Col xs={24} xl={15}>
              <Space direction="vertical" size={16} style={{ width: "100%" }}>
                <Card
                  title="账号列表"
                  extra={<Button onClick={() => refreshAdminData()}>刷新</Button>}
                >
                  <Table
                    columns={userColumns}
                    dataSource={users.map((item) => ({ ...item, key: item.userId }))}
                    pagination={false}
                    rowClassName={(record) =>
                      record.userId === selectedUserId ? "selected-row" : ""
                    }
                    onRow={(record) => ({
                      onClick: () => setSelectedUserId(record.userId)
                    })}
                  />
                </Card>

                <Card title="表单路由">
                  <List
                    dataSource={forms}
                    renderItem={(item) => (
                      <List.Item>
                        <List.Item.Meta
                          title={item.formName}
                          description={
                            <Space wrap>
                              <Text>ID: {item.formId}</Text>
                              <Tag color="gold">默认上传策略 {item.defaultUploadMode}</Tag>
                            </Space>
                          }
                        />
                      </List.Item>
                    )}
                  />
                </Card>
              </Space>
            </Col>

            <Col xs={24} xl={9}>
              <Space direction="vertical" size={16} style={{ width: "100%" }}>
                <Card title="新建账号">
                  <Form
                    form={createForm}
                    layout="vertical"
                    onFinish={handleCreateUser}
                    initialValues={{
                      enabled: true,
                      admin: false,
                      canUpload: true,
                      canDeleteCache: true
                    }}
                  >
                    <Form.Item
                      label="手机号"
                      name="phoneNumber"
                      rules={[{ required: true, message: "请输入手机号" }]}
                    >
                      <Input placeholder="13800000000" />
                    </Form.Item>
                    <Form.Item
                      label="姓名"
                      name="displayName"
                      rules={[{ required: true, message: "请输入姓名" }]}
                    >
                      <Input placeholder="工人姓名" />
                    </Form.Item>
                    <Form.Item
                      label="初始密码"
                      name="password"
                      rules={[{ required: true, message: "请输入初始密码" }]}
                    >
                      <Input.Password placeholder="至少 6 位" />
                    </Form.Item>
                    <Form.Item label="可用表单" name="formIds" rules={[{ required: true, message: "请选择至少一个表单" }]}>
                      <Select
                        mode="multiple"
                        options={forms.map((item) => ({
                          label: item.formName,
                          value: item.formId
                        }))}
                      />
                    </Form.Item>
                    <Form.Item label="启用账号" name="enabled" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item label="管理员" name="admin" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item label="允许上传" name="canUpload" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      label="允许删除缓存"
                      name="canDeleteCache"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" block>
                      保存账号
                    </Button>
                  </Form>
                </Card>

                <Card title="编辑权限">
                  {selectedUser ? (
                    <Space direction="vertical" size={16} style={{ width: "100%" }}>
                      <Alert
                        type="info"
                        showIcon
                        message={`${selectedUser.displayName} / ${selectedUser.phoneNumber}`}
                        description="可在这里按手机号配置表单入口、上传权限和清缓存权限。"
                      />
                      <Form form={permissionsForm} layout="vertical" onFinish={handleUpdatePermissions}>
                        <Form.Item label="可用表单" name="formIds" rules={[{ required: true, message: "请选择至少一个表单" }]}>
                          <Select
                            mode="multiple"
                            options={forms.map((item) => ({
                              label: item.formName,
                              value: item.formId
                            }))}
                          />
                        </Form.Item>
                        <Form.Item label="管理员" name="admin" valuePropName="checked">
                          <Switch />
                        </Form.Item>
                        <Form.Item label="允许上传" name="canUpload" valuePropName="checked">
                          <Switch />
                        </Form.Item>
                        <Form.Item
                          label="允许删除缓存"
                          name="canDeleteCache"
                          valuePropName="checked"
                        >
                          <Switch />
                        </Form.Item>
                        <Button type="primary" htmlType="submit" block>
                          保存权限
                        </Button>
                      </Form>

                      <Form form={passwordForm} layout="vertical" onFinish={handleResetPassword}>
                        <Form.Item
                          label="重置密码"
                          name="password"
                          rules={[{ required: true, message: "请输入新密码" }]}
                        >
                          <Input.Password placeholder="请输入新密码" />
                        </Form.Item>
                        <Button block htmlType="submit">
                          重置密码
                        </Button>
                      </Form>
                    </Space>
                  ) : (
                    <Text>请选择一个账号后再编辑权限。</Text>
                  )}
                </Card>
              </Space>
            </Col>
          </Row>
        </Spin>
      </Content>
    </Layout>
  );
}
