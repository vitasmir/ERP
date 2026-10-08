declare module "twig" {
  type Context = Record<string, unknown>;
  type Template = { render(context?: Context): string };
  const Twig: {
    twig(parameters: {
      id?: string;
      ref?: string;
      data?: string;
      allowInlineIncludes?: boolean;
      autoescape?: boolean | string;
      rethrow?: boolean;
      strict_variables?: boolean;
    }): Template;
    extendFilter(name: string, definition: (value: unknown, parameters: unknown[]) => unknown): void;
    extendFunction(name: string, definition: (...parameters: unknown[]) => unknown): void;
    cache(enabled: boolean): void;
  };
  export default Twig;
}
