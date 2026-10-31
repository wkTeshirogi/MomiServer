import * as React from "react"
import { Input as InputPrimitive } from "@base-ui/react/input"

import { cn } from "@/lib/utils"

type InputProps = React.ComponentProps<"input"> & {
  label?: React.ReactNode
}

function Input({ className, type, label, ...props }: InputProps) {
  if(type == "checkbox"){
    return (
    <label>
      <InputPrimitive
       v-model="isCheck"
       type="checkbox"
       className="peer sr-only"
       {...props}/>
      <span
        className={cn("block w-[2em] cursor-pointer bg-gray-500 rounded-full",
        "p-[1px] after:block after:h-[1em] after:w-[1em] after:rounded-full ",
        "after:bg-white after:transition peer-checked:bg-purple-500 ",
        "peer-checked:after:translate-x-[calc(100%-2px)] ",
        className)}
      ></span>
      {label}
    </label>
      )
  }

  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "h-9 w-full min-w-0 rounded-3xl border border-zinc-300 bg-white dark:border-zinc-700 dark:bg-zinc-900 px-3 py-1 text-base transition-[color,box-shadow,background-color] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )
}

export { Input }
