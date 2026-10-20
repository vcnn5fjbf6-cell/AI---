# Network Baseline

## Current Live Baseline

Source of truth: `C:\Users\10301\Documents\运维\network-architecture.md`.

Known ASA and procurement platform baseline:

| Item | Value |
| --- | --- |
| Firewall | Cisco ASA 5505 |
| Outside IP | `211.151.17.23/28` |
| Outside gateway | `211.151.17.17` |
| Current inside IP | `10.58.10.254/24` |
| Current procurement server IP | `10.58.10.1/24` |
| Current public NAT | `211.151.17.23:18080 -> 10.58.10.1:80` |
| Current ASDM/HTTPS management | `https://10.58.10.254/`, allowed from `10.58.10.0/24` |
| Public ASA SSH | Closed |
| Public ASA WebVPN/AnyConnect | Closed |

Current procurement deployment:

- Frontend Nginx container publishes `80:80`.
- Frontend Nginx proxies `/api/` to backend container `backend:8080`.
- Backend `8080`, MySQL `3306`, Redis `6379`, and Qdrant `6333/6334` are internal-only.

## Target VLAN Plan

| VLAN | Name | Subnet | Gateway | Purpose |
| --- | --- | --- | --- | --- |
| `10` | `MGMT` | `10.58.10.0/24` | `10.58.10.254` | Management terminals and network device management |
| `20` | `PROCUREMENT_DMZ` | `10.58.20.0/24` | `10.58.20.254` | Procurement application servers |
| `99` | `BMC` | `10.58.99.0/24` | `10.58.99.254` | Dell iDRAC/BMC |

Target public publishing after DMZ migration:

```text
211.151.17.23:18080 -> 10.58.20.1:80
```

Current pre-DMZ live mapping:

```text
211.151.17.23:18080 -> 10.58.10.1:80
```

## Target Topology

```text
Internet
  -> ASA Ethernet0/0 outside

ASA Ethernet0/1 trunk
  -> Cisco Catalyst 2960
      |-- access VLAN 10: management terminal / switch management
      |-- access VLAN 20: procurement server OS/business network
      |-- access VLAN 99: Dell iDRAC/BMC dedicated port
      |-- trunk/hybrid VLAN 20,99: Dell shared NIC scenario
```

## Policy Defaults

| Source | Destination | Policy |
| --- | --- | --- |
| `MGMT` | `PROCUREMENT_DMZ` | Allow required procurement access only |
| `MGMT` | `BMC` | Allow required iDRAC/IPMI/KVM management only |
| `PROCUREMENT_DMZ` | `MGMT` | Deny |
| `PROCUREMENT_DMZ` | `BMC` | Deny |
| `BMC` | `MGMT` / `PROCUREMENT_DMZ` | Deny by default |
| `outside` | `PROCUREMENT_DMZ` | Allow only public NAT service port |
| `outside` | `BMC` | Deny |

## Useful ASA Verification Commands

```text
show interface ip brief
show running-config interface
show switch vlan
show route
show nat
show access-list
show running-config access-group
show asp table socket
show running-config http
show running-config ssh
show running-config webvpn
```

## Cisco 2960 Planning Snippets

Example VLANs:

```text
vlan 10
 name MGMT
vlan 20
 name PROCUREMENT_DMZ
vlan 99
 name BMC
```

Example ASA uplink:

```text
interface Gi0/1
 description Uplink_to_ASA
 switchport mode trunk
 switchport trunk allowed vlan 10,20,99
```

Example access ports:

```text
interface Fa0/1
 description Mgmt_PC
 switchport mode access
 switchport access vlan 10

interface Fa0/2
 description Procurement_Server_OS
 switchport mode access
 switchport access vlan 20

interface Fa0/3
 description Dell_iDRAC_BMC
 switchport mode access
 switchport access vlan 99
```

Example shared NIC port:

```text
interface Fa0/2
 description Dell_Server_Shared_NIC_OS_and_iDRAC
 switchport mode trunk
 switchport trunk native vlan 20
 switchport trunk allowed vlan 20,99
```

For shared NIC, configure Dell iDRAC/BMC VLAN ID `99`.

## Dell iDRAC Batch Change Notes

Use Dell RACADM for bulk changes when credentials and reachability are available. Never print or store credentials in docs.

High-level sequence:

1. Build ASA VLAN 99 gateway and ACL.
2. Build 2960 VLAN 99 path.
3. Verify management terminal reaches old BMC addresses and new BMC subnet gateway.
4. Change one iDRAC as a pilot.
5. Verify new IP, gateway, VLAN, login, and remote console.
6. Continue in batches with a rollback plan.
