---
name: procurement-network-architecture
description: Maintain and implement the enterprise procurement platform internal network architecture. Use when working on this site's Cisco ASA 5505, Cisco Catalyst 2960, VLAN planning, DMZ segmentation, Dell iDRAC/BMC management network, public NAT/ACL exposure, ASDM/management access, or updates to C:\Users\10301\Documents\运维\network-architecture.md.
---

# Procurement Network Architecture

## Core Rule

Treat `C:\Users\10301\Documents\运维\network-architecture.md` as the running source of truth. Read it before proposing or changing ASA, switch, BMC, VLAN, NAT, or ACL settings, then update it after any architecture decision or live change.

Never record plaintext passwords, tokens, private keys, certificate private keys, or iDRAC credentials in the document or chat summaries.

## Mirror Copies

Maintain two identical copies of this skill:

- Auto-discovery copy: `C:\Users\10301\.codex\skills\procurement-network-architecture`
- Project archive copy: `D:\ai项目\采购\enterprise-procurement-platform\docs\skills\procurement-network-architecture`

When updating the skill, update the auto-discovery copy first, then synchronize the whole skill folder to the project archive copy. Validate both copies with `quick_validate.py` after synchronization.

## Baseline

Read `references/network-baseline.md` when the task involves:

- Changing ASA NAT, ACL, VLAN, interface, ASDM, SSH, WebVPN, or routing.
- Planning or configuring Cisco 2960 ports.
- Separating management, procurement service, and Dell BMC/iDRAC networks.
- Deciding public ports for the procurement platform.
- Bulk-changing Dell iDRAC/BMC IP, gateway, VLAN ID, or shared NIC mode.

## Workflow

1. Establish whether the request is documentation-only, planning-only, or a live network change.
2. Read the current `network-architecture.md` and, when relevant, `references/network-baseline.md`.
3. Separate current state from target state. Mark unimplemented items as planned or pending.
4. For live changes, confirm the management path is available before changing forwarding, ACLs, VLANs, or management access.
5. Prefer least exposure:
   - Public internet reaches only the published procurement service port.
   - ASA management is inside-only.
   - BMC/iDRAC is management-only and never exposed to outside or procurement DMZ.
   - Backend, database, Redis, Qdrant, and BMC ports are not opened to outside.
6. After any live change, verify with read-only commands and save configuration when appropriate.
7. Update `network-architecture.md` with the exact decision, current/planned status, and validation result.

## Design Defaults

Use this target segmentation unless the user explicitly changes it:

- VLAN 10 `MGMT`: `10.58.10.0/24`, gateway `10.58.10.254`
- VLAN 20 `PROCUREMENT_DMZ`: `10.58.20.0/24`, gateway `10.58.20.254`
- VLAN 99 `BMC`: `10.58.99.0/24`, gateway `10.58.99.254`

Use ASA as the layer-3 gateway and policy boundary. Use Cisco 2960 as layer-2 VLAN access/trunk only.

Expected security policy:

- `MGMT -> PROCUREMENT_DMZ`: allow only required service/management ports.
- `MGMT -> BMC`: allow only required iDRAC/IPMI/KVM management ports.
- `PROCUREMENT_DMZ -> MGMT`: deny by default.
- `PROCUREMENT_DMZ -> BMC`: deny.
- `BMC -> MGMT/PROCUREMENT_DMZ`: deny by default.
- `outside -> PROCUREMENT_DMZ`: allow only the public NAT service port.
- `outside -> BMC`: deny.

For the procurement platform, keep the application server listening on internal TCP/80 and publish a high external port when requested, currently planned as:

```text
211.151.17.23:18080 -> procurement server:80
```

## Device Handling

For ASA 5505:

- Check console port availability before live work.
- Use read-only `show` commands before and after changes.
- Be careful with interface/VLAN changes that can cut off management.
- Keep inside ASDM/HTTPS management restricted to management subnet.
- Do not re-enable public SSH or WebVPN unless the user explicitly asks and accepts the exposure.

For Cisco 2960:

- Treat it as a VLAN access switch, not the policy enforcement point.
- Use trunk from ASA to 2960 carrying VLAN 10, 20, and 99.
- Use access ports for simple endpoints.
- For Dell shared NIC/iDRAC, use trunk/hybrid behavior: native/business VLAN 20 and tagged BMC VLAN 99, if supported.

For Dell iDRAC/BMC:

- Prefer dedicated BMC ports when available.
- If using shared NIC, confirm iDRAC VLAN support before relying on isolation.
- Batch changes should be staged: build ASA/2960 path first, then change BMC IP/VLAN one host at a time and verify reachability.

## Documentation Pattern

When updating `network-architecture.md`, include:

- What is already live.
- What is planned but not implemented.
- Address/VLAN/port tables.
- NAT and ACL snippets.
- Validation commands or limitations.
- A dated change-record row.

Keep diagrams high level. Put command snippets near the section they support.
