import type { App, DirectiveBinding } from "vue";
import { hasPermission } from "../utils/permission";

type PermValue = string | string[];

function applyPermission(el: HTMLElement, binding: DirectiveBinding<PermValue>): void {
  const required = binding.value;
  if (!required) {
    return;
  }
  const pass = hasPermission(required);
  el.style.display = pass ? "" : "none";
}

export function registerPermissionDirective(app: App): void {
  app.directive("perm", {
    mounted(el: HTMLElement, binding: DirectiveBinding<PermValue>) {
      applyPermission(el, binding);
    },
    updated(el: HTMLElement, binding: DirectiveBinding<PermValue>) {
      applyPermission(el, binding);
    }
  });
}
