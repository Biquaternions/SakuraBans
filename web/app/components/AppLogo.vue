<template>
  <span class="inline-flex items-center gap-2 font-semibold tracking-tight text-highlighted">
    <svg
      viewBox="0 0 32 32"
      class="size-6 shrink-0"
      aria-hidden="true"
    >
      <g fill="var(--ui-primary)">
        <path
          v-for="angle in [0, 72, 144, 216, 288]"
          :key="angle"
          :transform="`rotate(${angle} 16 16)`"
          d="M16 15c-3.2-2.2-4.6-5.4-3.6-8.4.6-1.8 2-2.8 3.6-1.6 1.6-1.2 3-.2 3.6 1.6 1 3-.4 6.2-3.6 8.4z"
        />
      </g>
      <circle
        cx="16"
        cy="16"
        r="2"
        fill="var(--ui-bg)"
      />
    </svg>
    <span>SakuraBans</span>
  </span>
</template>
